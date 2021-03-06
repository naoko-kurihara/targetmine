package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author chenyian
 */
public class GwasConverter extends BioFileConverter {
	private static final Logger LOG = Logger.getLogger(GwasConverter.class);

	private static final Double MIN_PVALUE = Double.valueOf("1E-300");
	//
	private static final String DATASET_TITLE = "NHGRI GWAS Catalog";
	private static final String DATA_SOURCE_NAME = "NHGRI GWAS Catalog";

	private Map<String, String> diseaseMap = new HashMap<String, String>();

	private Map<String, String> geneMap = new HashMap<String, String>();
	private Map<String, String> gwaMap = new HashMap<String, String>();
	private Map<String, String> pubMap = new HashMap<String, String>();
	private Map<String, String> doMap = new HashMap<String, String>();
	private Map<String, Item> snpMap = new HashMap<String, Item>();

	private File diseaseMapFile;

	public void setDiseaseMapFile(File diseaseMapFile) {
		this.diseaseMapFile = diseaseMapFile;
	}

	/**
	 * Constructor
	 * 
	 * @param writer
	 *            the ItemWriter used to handle the resultant items
	 * @param model
	 *            the Model
	 */
	public GwasConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * 
	 * 
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		if (diseaseMap.isEmpty()) {
			LOG.info("Read diseaseMap file......");
			readDiseaseMap();
		}
		Iterator<String[]> iterator = FormattedTextParser
				.parseTabDelimitedReader(new BufferedReader(reader));
		// sikp header
		iterator.next();
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			if (cols.length < 2) {
				continue;
			}
			String snpGeneId = cols[17];
			// skip those intergenic snp (when snpGeneId column is empty)
			if (!StringUtils.isEmpty(snpGeneId)) {
				String gwaRefId = getGenomeWideAssociation(cols[7].trim(), cols[1], cols[27]);
				Item snp = getSnp(cols[21], cols[24]);
				for (String geneId : snpGeneId.split(";")) {
					snp.addToCollection("genes", getGene(geneId));
				}
				snp.addToCollection("genomeWideAssociations", gwaRefId);
			}
		}
	}
	
	@Override
	public void close() throws Exception {
		store(snpMap.values());
	}

	private Item getSnp(String dbSnpId, String context) {
		Item ret = snpMap.get(dbSnpId);
		if (ret == null) {
			ret = createItem("SNP");
			ret.setAttribute("identifier", dbSnpId);
			if (!StringUtils.isEmpty(context)) {
				ret.setAttribute("context", context);
			}
			snpMap.put(dbSnpId, ret);
		}
		return ret;
	}

	private String getGene(String geneId) throws ObjectStoreException {
		String ret = geneMap.get(geneId);
		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", geneId);
			item.setAttribute("ncbiGeneId", geneId);
			store(item);
			ret = item.getIdentifier();
			geneMap.put(geneId, ret);
		}
		return ret;
	}

	private String getPublication(String pubMedId) throws ObjectStoreException {
		String ret = pubMap.get(pubMedId);
		if (ret == null) {
			Item item = createItem("Publication");
			item.setAttribute("pubMedId", pubMedId);
			store(item);
			ret = item.getIdentifier();
			pubMap.put(pubMedId, ret);
		}
		return ret;
	}

	private String getGenomeWideAssociation(String trait, String pubMedId, String pvalue)
			throws ObjectStoreException {
		String ret = gwaMap.get(trait + pubMedId);
		if (ret == null) {
			Item item = createItem("GenomeWideAssociation");
			item.setAttribute("trait", trait);
			if (!pvalue.equals("NS")){
				if (Double.valueOf(pvalue).compareTo(MIN_PVALUE) < 0 && !pvalue.equals("0")) {
					item.setAttribute("pvalue", MIN_PVALUE.toString());
					LOG.info(String.format("p-value CHANGED: %s (%s), original p-value: %s", trait, pubMedId, pvalue));
				} else {
					item.setAttribute("pvalue", pvalue);
				}
			}
			item.setReference("publication", getPublication(pubMedId));
			String doTerms = diseaseMap.get(trait);
			if (doTerms != null) {
				String[] terms = doTerms.split(",");
				for (String term : terms) {
					item.addToCollection("doTerms", getDoTerm(term));
				}
			} else {
				LOG.info("DOTerm for '" + trait + "' not found. ");
			}
			store(item);
			ret = item.getIdentifier();
			gwaMap.put(trait + pubMedId, ret);
		}
		return ret;
	}

	private String getDoTerm(String doId) throws ObjectStoreException {
		String ret = doMap.get(doId);
		if (ret == null) {
			Item item = createItem("DOTerm");
			item.setAttribute("identifier", doId);
			store(item);
			ret = item.getIdentifier();
			doMap.put(doId, ret);
		}
		return ret;
	}

	private void readDiseaseMap() {
			Iterator<String[]> iterator;
			try {
				iterator = FormattedTextParser
						.parseTabDelimitedReader(new BufferedReader(new FileReader(diseaseMapFile)));
				while (iterator.hasNext()) {
					String[] cols = iterator.next();
					if (cols.length < 2 || StringUtils.isEmpty(cols[1])) {
						continue;
					}
					diseaseMap.put(cols[0].trim(), cols[1]);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				throw new RuntimeException("Disease mapping file not found.");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

}
