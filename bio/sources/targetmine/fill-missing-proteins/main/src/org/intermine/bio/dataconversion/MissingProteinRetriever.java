package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tools.ant.BuildException;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.StringUtil;
import org.intermine.model.bio.Protein;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.SimpleConstraint;
import org.intermine.xml.full.FullRenderer;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ItemFactory;

/**
 * Class to fill in proteins information from ebi-dbfetch
 * 
 * @author chenyian
 */
public class MissingProteinRetriever {
	protected static final Logger LOG = Logger.getLogger(MissingProteinRetriever.class);
	// see http://www.ebi.ac.uk/Tools/dbfetch/dbfetch for details
	protected static final String DBFETCH_URL = "http://www.ebi.ac.uk/Tools/dbfetch/dbfetch?db=UniProtKB&style=raw&id=";
	// number of summaries to retrieve per request
	protected static final int BATCH_SIZE = 200;

	// number of times to try the same bacth from the server
	// private static final int MAX_TRIES = 5;

	private Pattern SQ_PATTERN = Pattern.compile("SQ   SEQUENCE\\s+(\\d+) AA;\\s+(\\d+) MW;.*");

	private Set<String> createdProteinAccs = new HashSet<String>();

	private String osAlias = null;
	private String outputFile = null;

	private Map<String, Item> organismMap = new HashMap<String, Item>();

	private static final int RETRY = 10;

	/**
	 * Set the ObjectStore alias.
	 * 
	 * @param osAlias
	 *            The ObjectStore alias
	 */
	public void setOsAlias(String osAlias) {
		this.osAlias = osAlias;
	}

	/**
	 * Set the output file name
	 * 
	 * @param outputFile
	 *            The output file name
	 */
	public void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
	}

	/**
	 * Retrieve protein details from EBI dbfetch by the primaryAccessions 
	 * and fill in the details to the protein objects.
	 * 
	 * @throws BuildException
	 *             if an error occurs
	 */
	public void execute() {
		// Needed so that STAX can find it's implementation classes
		ClassLoader cl = Thread.currentThread().getContextClassLoader();

		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

		if (osAlias == null) {
			throw new BuildException("osAlias attribute is not set");
		}
		if (outputFile == null) {
			throw new BuildException("outputFile attribute is not set");
		}

		LOG.info("Starting MissingProteinRetriever");

		Writer writer = null;

		try {
			writer = new FileWriter(outputFile);

			ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

			List<Protein> proteins = getProteins(os);

			LOG.info("There are " + proteins.size() + " protein(s) without primaryIdentifier.");

			Set<String> accIds = new HashSet<String>();
			Set<Item> toStore = new HashSet<Item>();

			ItemFactory itemFactory = new ItemFactory(os.getModel(), "-1_");
			writer.write(FullRenderer.getHeader() + "\n");
			for (Iterator<Protein> i = proteins.iterator(); i.hasNext();) {
				Protein protein = (Protein) i.next();
				accIds.add(protein.getPrimaryAccession());
				if (accIds.size() == BATCH_SIZE || !i.hasNext()) {
					LOG.info("Querying EBI Dbfetch for " + accIds.size() + "proteins.");
					BufferedReader br = new BufferedReader(getReader(accIds));

					String l;
					boolean isEntry = false;
					ProteinHolder ph = new ProteinHolder();
					
					while ((l = br.readLine()) != null) {
						if (l.trim().equals("//")) {
							// skip header part if there is
							if (!isEntry) {
								isEntry = true;
								continue;
							}
							for (String pAcc : ph.accessions) {
								// create protein; skip if we've already created this item
								if (!createdProteinAccs.contains(pAcc)) {
									Item p = itemFactory.makeItemForClass("Protein");
									// TODO should define primaryIdentifier more carefully
									p.setAttribute("primaryAccession", pAcc);
									if (pAcc.equals(ph.primaryAccession)) {
										p.setAttribute("primaryIdentifier", ph.primaryIdentifier);
										p.setAttribute("uniprotName", ph.primaryIdentifier);
										p.setAttribute("uniprotAccession", pAcc);
										p.setAttribute("isUniprotCanonical", "true");
									} else {
										p.setAttribute("primaryIdentifier", pAcc + ph.primaryIdentifier.substring(ph.primaryIdentifier.indexOf("_")));
										p.setAttribute("uniprotName", "Synonym of " + ph.primaryAccession);
										p.setAttribute("uniprotAccession", ph.primaryAccession);
										p.setAttribute("isUniprotCanonical", "false");
									}
									p.setReference("organism", getOrganism(ph.taxonId, itemFactory));

									if (!StringUtils.isEmpty(ph.name)) {
										p.setAttribute("name", ph.name);
									}
									p.setAttribute("molecularWeight", ph.molecularWeight);
									p.setAttribute("length", ph.length);

									toStore.add(p);
									createdProteinAccs.add(pAcc);
								} else {
									LOG.info("Already created. Skip the entry: " + ph);
								}
							}

							ph = new ProteinHolder();
							continue;
						}
						
						// remove the ECO information
						if (l.contains("{ECO:")) {
							l = l.replaceAll("\\{ECO:.+\\}", "");
						}

						if (l.startsWith("ID")) {
							String[] split = l.split("\\s+");
							ph.primaryIdentifier = split[1];
						} else if (l.startsWith("AC")) {
							String accs = l.substring(5);
							String[] split = accs.split(";");
							ph.primaryAccession = split[0];
							for (String acc : split) {
								// ignore those not in our missing list
								if (accIds.contains(acc.trim())) {
									ph.accessions.add(acc.trim());
								}
							}
						} else if (l.startsWith("OX")) {
							ph.taxonId = l.substring(l.indexOf("=") + 1, l.indexOf(";")).trim();
						} else if (l.startsWith("DE") && StringUtils.isEmpty(ph.name)) {
							// chenyian: there are some more complated case, see the following example:
							// http://www.ebi.ac.uk/Tools/dbfetch/dbfetch?db=UniProtKB&style=raw&id=P19096
							// So far just ignore it ...
							String de = l.substring(5);
							if (de.startsWith("RecName")) {
								ph.name = de.substring(de.indexOf("=") + 1, de.indexOf(";")).trim();
							} else if (de.startsWith("SubName") && ph.name == null) {
								ph.name = de.substring(de.indexOf("=") + 1, de.indexOf(";")).trim();
							}
						} else if (l.startsWith("SQ")) {
							Matcher matcher = SQ_PATTERN.matcher(l);
							if (matcher.matches()) {
								ph.length = matcher.group(1);
								ph.molecularWeight = matcher.group(2);
							}
						}
					}
					br.close();

					for (Iterator<Item> iter = toStore.iterator(); iter.hasNext();) {
						Item item = iter.next();
						writer.write(FullRenderer.render(item));
					}
					accIds.clear();
					toStore.clear();
				}
			}

			// save the Organism object
			for (Iterator<Item> iter = organismMap.values().iterator(); iter.hasNext();) {
				Item item = iter.next();
				writer.write(FullRenderer.render(item));
			}

			writer.write(FullRenderer.getFooter() + "\n");
		} catch (Exception e) {
			throw new BuildException("exception while retrieving proteins", e);
		} finally {
			Thread.currentThread().setContextClassLoader(cl);
			if (writer != null) {
				try {
					writer.close();
				} catch (Exception e) {
					// ignore
				}
			}
		}
	}

	/**
	 * Retrieve the proteins to be updated
	 * 
	 * @param os
	 *            the ObjectStore to read from
	 * @return a List of Protein object
	 */
	protected List<Protein> getProteins(ObjectStore os) {
		Query q = new Query();
		QueryClass qc = new QueryClass(Protein.class);
		q.addFrom(qc);
		q.addToSelect(qc);

		SimpleConstraint sc = new SimpleConstraint(new QueryField(qc, "primaryIdentifier"),
				ConstraintOp.IS_NULL);

		q.setConstraint(sc);

		@SuppressWarnings({ "unchecked", "rawtypes" })
		List<Protein> ret = (List<Protein>) ((List) os.executeSingleton(q));

		return ret;
	}

	/**
	 * Obtain the uniprot information for the protein
	 * 
	 * @param primaryAccessions
	 *            the primary accessions of the proteins
	 * @return a Reader for the information
	 * @throws Exception
	 *             if an error occurs
	 */
	protected Reader getReader(Set<String> primaryAccessions) throws Exception {
		String queryString = StringUtil.join(primaryAccessions, ",");
		URL url = new URL(DBFETCH_URL + queryString);
		int i = 1;
		while (true) {
			try {
				// TODO for tracing the miss-plant bug
				LOG.info(queryString);
				return new BufferedReader(new InputStreamReader(url.openStream()));
			} catch (IOException e) {
				i++;
				if (i > RETRY) {
					throw new RuntimeException("Unable to read from Uniprot, after tried " + RETRY
							+ " times. " + url.toString());
				}
				continue;
			}
		}
	}

	private class ProteinHolder {
		Set<String> accessions = new HashSet<String>();
		String primaryAccession;
		String taxonId;
		String primaryIdentifier;
		String name;
		String ecNumber;
		String molecularWeight;
		String length;

		@Override
		public String toString() {
			return String.format("%s, %s(%s), %s, [%s|%s], %s",
					StringUtils.join(accessions, "/"), primaryIdentifier, taxonId, ecNumber,
					length, molecularWeight, name);
		}

	}

	private Item getOrganism(String taxonId, ItemFactory itemFactory) {
		Item ret = organismMap.get(taxonId);
		if (ret == null) {
			ret = itemFactory.makeItemForClass("Organism");
			ret.setAttribute("taxonId", taxonId);
			organismMap.put(taxonId, ret);
		}
		return ret;
	}
}
