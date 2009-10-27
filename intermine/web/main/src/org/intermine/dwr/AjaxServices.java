package org.intermine.dwr;

/*
 * Copyright (C) 2002-2009 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.queryParser.ParseException;
import org.apache.struts.Globals;
import org.apache.struts.util.MessageResources;
import org.directwebremoting.WebContext;
import org.directwebremoting.WebContextFactory;
import org.intermine.InterMineException;
import org.intermine.api.bag.BagManager;
import org.intermine.api.bag.BagQueryConfig;
import org.intermine.api.bag.TypeConverter;
import org.intermine.api.bag.TypeConverterHelper;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.api.profile.ProfileAlreadyExistsException;
import org.intermine.api.profile.ProfileManager;
import org.intermine.api.profile.SavedQuery;
import org.intermine.api.profile.TagManager;
import org.intermine.api.results.WebTable;
import org.intermine.api.search.SearchFilterEngine;
import org.intermine.api.search.SearchRepository;
import org.intermine.api.search.WebSearchable;
import org.intermine.api.tag.TagNames;
import org.intermine.api.template.TemplatePrecomputeHelper;
import org.intermine.api.template.TemplateQuery;
import org.intermine.api.util.NameUtil;
import org.intermine.metadata.FieldDescriptor;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.intermine.ObjectStoreInterMineImpl;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QuerySelectable;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.pathquery.Path;
import org.intermine.pathquery.PathQuery;
import org.intermine.util.StringUtil;
import org.intermine.util.TypeUtil;
import org.intermine.web.autocompletion.AutoCompleter;
import org.intermine.web.logic.Constants;
import org.intermine.web.logic.config.Type;
import org.intermine.web.logic.config.WebConfig;
import org.intermine.web.logic.query.PageTableQueryMonitor;
import org.intermine.web.logic.query.QueryCreationHelper;
import org.intermine.web.logic.query.QueryMonitorTimeout;
import org.intermine.web.logic.results.PagedTable;
import org.intermine.web.logic.results.WebState;
import org.intermine.web.logic.session.QueryCountQueryMonitor;
import org.intermine.web.logic.session.SessionMethods;
import org.intermine.web.logic.widget.EnrichmentWidget;
import org.intermine.web.logic.widget.GraphWidget;
import org.intermine.web.logic.widget.GridWidget;
import org.intermine.web.logic.widget.HTMLWidget;
import org.intermine.web.logic.widget.TableWidget;
import org.intermine.web.logic.widget.config.EnrichmentWidgetConfig;
import org.intermine.web.logic.widget.config.GraphWidgetConfig;
import org.intermine.web.logic.widget.config.GridWidgetConfig;
import org.intermine.web.logic.widget.config.HTMLWidgetConfig;
import org.intermine.web.logic.widget.config.TableWidgetConfig;
import org.intermine.web.logic.widget.config.WidgetConfig;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;


/**
 * This class contains the methods called through DWR Ajax
 *
 * @author Xavier Watkins
 *
 */
public class AjaxServices
{
    protected static final Logger LOG = Logger.getLogger(AjaxServices.class);
    private static final Object ERROR_MSG = "Error happened during DWR ajax service.";
    private static final String INVALID_NAME_MSG = "Invalid name.  Names may only contain letters, "
        + "numbers, spaces, and underscores.";
    /**
     * Creates a favourite Tag for the given templateName
     *
     * @param name the name of the template we want to set as a favourite
     * @param type type of tag (bag or template)
     * @param isFavourite whether or not this item is currently a favourite
     */
    public void setFavourite(String name, String type, boolean isFavourite) {
        try {
            WebContext ctx = WebContextFactory.get();
            HttpSession session = ctx.getSession();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            String nameCopy = name.replaceAll("#039;", "'");
            TagManager tagManager = getTagManager();

            // already a favourite.  turning off.
            if (isFavourite) {
                tagManager.deleteTag(TagNames.IM_FAVOURITE, nameCopy, type, profile.getUsername());
            // not a favourite.  turning on.
            } else {
                tagManager.addTag(TagNames.IM_FAVOURITE, nameCopy, type, profile.getUsername());
            }
        } catch (RuntimeException e) {
            processException(e);
        }
    }

    private static void processException(Exception e) {
        LOG.error(ERROR_MSG, e);
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        throw new RuntimeException(e);
    }

    /**
     * Precomputes the given template query
     * @param templateName the template query name
     * @return a String to guarantee the service ran properly
     */
    public String preCompute(String templateName) {
        try {
            WebContext ctx = WebContextFactory.get();
            HttpSession session = ctx.getSession();
            ServletContext servletContext = ctx.getServletContext();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            Map<String, TemplateQuery> templates = profile.getSavedTemplates();
            TemplateQuery template = templates.get(templateName);
            ObjectStoreInterMineImpl os = (ObjectStoreInterMineImpl) servletContext
                    .getAttribute(Constants.OBJECTSTORE);
            List indexes = new ArrayList();
            Query query = TemplatePrecomputeHelper.getPrecomputeQuery(template, indexes, null);

            try {
                if (!os.isPrecomputed(query, "template")) {
                    session.setAttribute("precomputing_" + templateName, "true");
                    os.precompute(query, indexes, "template");
                }
            } catch (ObjectStoreException e) {
                LOG.error("Error while precomputing", e);
            } finally {
                session.removeAttribute("precomputing_" + templateName);
            }
        } catch (RuntimeException e) {
            processException(e);
        }
        return "precomputed";
    }

    /**
     * Summarises the given template query.
     *
     * @param templateName the template query name
     * @return a String to guarantee the service ran properly
     */
    public String summarise(String templateName) {
        try {
            WebContext ctx = WebContextFactory.get();
            HttpSession session = ctx.getSession();
            ServletContext servletContext = ctx.getServletContext();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            Map<String, TemplateQuery> templates = profile.getSavedTemplates();
            TemplateQuery template = templates.get(templateName);
            ObjectStoreInterMineImpl os = (ObjectStoreInterMineImpl) servletContext
                    .getAttribute(Constants.OBJECTSTORE);
            ObjectStoreWriter osw = ((ProfileManager) servletContext.getAttribute(
                        Constants.PROFILE_MANAGER)).getProfileObjectStoreWriter();
            try {
                session.setAttribute("summarising_" + templateName, "true");
                template.summarise(os, osw);
            } catch (ObjectStoreException e) {
                LOG.error("Failed to summarise " + templateName, e);
            } catch (NullPointerException e) {
                NullPointerException e2 = new NullPointerException("No such template "
                        + templateName);
                e2.initCause(e);
                throw e2;
            } finally {
                session.removeAttribute("summarising_" + templateName);
            }
        } catch (RuntimeException e) {
            processException(e);
        }
        return "summarised";
    }

    /**
     * Rename a element such as history, name, bag
     * @param name the name of the element
     * @param type history, saved, bag
     * @param reName the new name for the element
     * @return the new name of the element as a String
     * @exception Exception if the application business logic throws
     *  an exception
     */
    public String rename(String name, String type, String reName)
        throws Exception {
        String newName;
        try {
            newName = reName.trim();
            WebContext ctx = WebContextFactory.get();
            HttpSession session = ctx.getSession();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            SavedQuery sq;
            if (name.equals(newName) || StringUtils.isEmpty(newName)) {
                return name;
            }
            // TODO get error text from properties file
            if (!NameUtil.isValidName(newName)) {
                return INVALID_NAME_MSG;
            }
            if (type.equals("history")) {
                if (profile.getHistory().get(name) == null) {
                    return "<i>" + name + " does not exist</i>";
                }
                if (profile.getHistory().get(newName) != null) {
                    return "<i>" + newName + " already exists</i>";
                }
                profile.renameHistory(name, newName);
            } else if (type.equals("saved")) {
                if (profile.getSavedQueries().get(name) == null) {
                    return "<i>" + name + " does not exist</i>";
                }
                if (profile.getSavedQueries().get(newName) != null) {
                    return "<i>" + newName + " already exists</i>";
                }
                sq = profile.getSavedQueries().get(name);
                profile.deleteQuery(sq.getName());
                sq = new SavedQuery(newName, sq.getDateCreated(), sq.getPathQuery());
                profile.saveQuery(sq.getName(), sq);
            } else if (type.equals("bag")) {
                try {
                    profile.renameBag(name, newName);
                } catch (IllegalArgumentException e) {
                    return "<i>" + name + " does not exist</i>";
                } catch (ProfileAlreadyExistsException e) {
                    return "<i>" + newName + " already exists</i>";
                }
            } else {
                return "Type unknown";
            }
            return newName;
        } catch (RuntimeException e) {
            processException(e);
            return null;
        }
    }


    /**
     * For a given bag, set its description
     * @param bagName the bag
     * @param description the description as entered by the user
     * @return the description for display on the jsp page
     * @throws Exception an exception
     */
    public String saveBagDescription(String bagName, String description) throws Exception {
        try {
            WebContext ctx = WebContextFactory.get();
            HttpSession session = ctx.getSession();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            InterMineBag bag = profile.getSavedBags().get(bagName);
            if (bag == null) {
                throw new InterMineException("List \"" + bagName + "\" not found.");
            }
            bag.setDescription(description);
            profile.getSearchRepository().descriptionChanged(bag);
            return description;
        } catch (RuntimeException e) {
            processException(e);
            return null;
        }
    }

    /**
     * Set the description of a view path.
     * @param pathString the string representation of the path
     * @param description the new description
     * @return the description, or null if the description was empty
     */
    public String changeViewPathDescription(String pathString, String description) {
        try {
            String descr = description;
            if (description.trim().length() == 0) {
                descr = null;
            }
            WebContext ctx = WebContextFactory.get();
            HttpSession session = ctx.getSession();
            PathQuery query = (PathQuery) session.getAttribute(Constants.QUERY);
            Path path = PathQuery.makePath(query.getModel(), query, pathString);
            Path prefixPath = path.getPrefix();
            if (descr == null) {
                query.getPathDescriptions().remove(prefixPath);
            } else {
                query.getPathDescriptions().put(prefixPath, descr);
            }
            if (descr == null) {
                return null;
            }
            return descr.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
        } catch (RuntimeException e) {
            processException(e);
            return null;
        }
    }

    /*
     * Cannot be refactored from AjaxServices, else WebContextFactory.get() returns null
     */
    private static WebState getWebState() {
        HttpSession session = WebContextFactory.get().getSession();
        return SessionMethods.getWebState(session);
    }

    /**
     * Get the summary for the given column
     * @param summaryPath the path for the column as a String
     * @param tableName name of column-owning table
     * @return a collection of rows
     * @throws Exception an exception
     */
    public static List getColumnSummary(String tableName, String summaryPath) throws Exception {
        try {
            WebContext ctx = WebContextFactory.get();
            HttpSession session = ctx.getSession();
            ServletContext servletContext = session.getServletContext();
            ObjectStore os = (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE);
            BagManager bagManager = SessionMethods.getBagManager(servletContext);
            
            WebTable webTable = (SessionMethods.getResultsTable(session, tableName))
                                   .getWebTable();
            PathQuery pathQuery = webTable.getPathQuery();

            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            Map<String, InterMineBag> allBags = bagManager.getUserAndGlobalBags(profile);

            Query distinctQuery = QueryCreationHelper.makeSummaryQuery(pathQuery, allBags,
                    new HashMap<String, QuerySelectable>(), summaryPath, servletContext);
            Results results = os.execute(distinctQuery);

            // Start the count of results
            Query countQuery = QueryCreationHelper.makeSummaryQuery(pathQuery, allBags,
                    new HashMap<String, QuerySelectable>(), summaryPath, servletContext);
            QueryCountQueryMonitor clientState
                = new QueryCountQueryMonitor(Constants.QUERY_TIMEOUT_SECONDS * 1000, countQuery);
            MessageResources messages = (MessageResources) ctx.getHttpServletRequest()
                                                              .getAttribute(Globals.MESSAGES_KEY);
            String qid = SessionMethods.startQueryCount(clientState, session, messages);
            List<ResultsRow> pageSizeResults = new ArrayList<ResultsRow>();
            int rowCount = 0;
            for (ResultsRow row : (List<ResultsRow>) results) {
                pageSizeResults.add(row);
                rowCount++;
                if (rowCount >= Constants.DEFAULT_TABLE_SIZE) {
                    break;
                }
            }
            return Arrays.asList(new Object[] {
                        pageSizeResults, qid, new Integer(results.size())
                    });
        } catch (RuntimeException e) {
            processException(e);
            return null;
        }
    }

    /**
     * Return the number of rows of results from the query with the given query id.  If the size
     * isn't yet available, return null.  The query must be started with
     * SessionMethods.startPagedTableCount().
     * @param qid the id
     * @return the row count or null if not yet available
     */
    public static Integer getResultsSize(String qid) {
        try {
            WebContext ctx = WebContextFactory.get();
            HttpSession session = ctx.getSession();
            QueryMonitorTimeout controller = (QueryMonitorTimeout)
                SessionMethods.getRunningQueryController(qid, session);

            // this could happen if the user navigates away then back to the page
            if (controller == null) {
                return null;
            }

            // First tickle the controller to avoid timeout
            controller.tickle();

            if (controller.isCancelledWithError()) {
                LOG.debug("query qid " + qid + " error");
                return null;
            } else if (controller.isCancelled()) {
                LOG.debug("query qid " + qid + " cancelled");
                return null;
            } else if (controller.isCompleted()) {
                LOG.debug("query qid " + qid + " complete");

                if (controller instanceof PageTableQueryMonitor) {
                    PagedTable pt = ((PageTableQueryMonitor) controller).getPagedTable();
                    return new Integer(pt.getExactSize());
                }
                if (controller instanceof QueryCountQueryMonitor) {
                    return new Integer(((QueryCountQueryMonitor) controller).getCount());
                }
                LOG.debug("query qid " + qid + " - unknown controller type");
                return null;
            } else {
                // query still running
                LOG.debug("query qid " + qid + " still running, making client wait");
                return null;
            }
        } catch (RuntimeException e) {
            processException(e);
            return null;
        }
    }

    /**
     * Given a scope, type, tags and some filter text, produce a list of matching WebSearchable, in
     * a format useful in JavaScript.  Each element of the returned List is a List containing a
     * WebSearchable name, a score (from Lucene) and a string with the matching parts of the
     * description highlighted.
     * @param scope the scope (from TemplateHelper.GLOBAL_TEMPLATE or TemplateHelper.USER_TEMPLATE,
     * even though not all WebSearchables are templates)
     * @param type the type (from TagTypes)
     * @param tags the tags to filter on
     * @param filterText the text to pass to Lucene
     * @param filterAction toggles favourites filter off an on; will be blank or 'favourites'
     * @param callId unique id
     * @return a List of Lists
     */
    public static List<String> filterWebSearchables(String scope, String type,
                                                    List<String> tags, String filterText,
                                                    String filterAction, String callId) {
        try {
            ServletContext servletContext = WebContextFactory.get().getServletContext();
            ProfileManager pm = SessionMethods.getProfileManager(servletContext);
            HttpSession session = WebContextFactory.get().getSession();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            Map<String, WebSearchable> wsMap;
            Map<WebSearchable, Float> hitMap = new LinkedHashMap<WebSearchable, Float>();
            Map<WebSearchable, String> highlightedDescMap = new HashMap<WebSearchable, String>();

            if (filterText != null && filterText.length() > 1) {
                wsMap = new LinkedHashMap<String, WebSearchable>();
                //Map<WebSearchable, String> scopeMap = new LinkedHashMap<WebSearchable, String>();
                SearchRepository globalSearchRepository =
                    SessionMethods.getGlobalSearchRepository(servletContext);
                try {
                    long time =
                        SearchRepository.runLeuceneSearch(filterText, scope, type, profile,
                                                        globalSearchRepository,
                                                        hitMap, null, highlightedDescMap);
                    LOG.info("Lucene search took " + time + " milliseconds");
                } catch (ParseException e) {
                    LOG.error("couldn't run lucene filter", e);
                    ArrayList<String> emptyList = new ArrayList<String>();
                    emptyList.add(callId);
                    return emptyList;
                } catch (IOException e) {
                    LOG.error("couldn't run lucene filter", e);
                    ArrayList<String> emptyList = new ArrayList<String>();
                    emptyList.add(callId);
                    return emptyList;
                }

                //long time = System.currentTimeMillis();

                for (WebSearchable ws: hitMap.keySet()) {
                    wsMap.put(ws.getName(), ws);
                }
            } else {

                if (scope.equals("user")) {
                    SearchRepository searchRepository = profile.getSearchRepository();
                    wsMap = (Map<String, WebSearchable>) searchRepository.getWebSearchableMap(type);
                } else {
                    SearchRepository globalRepository =
                        (SearchRepository) servletContext.getAttribute(Constants.
                                                                       GLOBAL_SEARCH_REPOSITORY);
                    if (scope.equals("global")) {
                        wsMap = (Map<String, WebSearchable>) globalRepository.
                            getWebSearchableMap(type);
                    } else {
                        // must be "all"
                        SearchRepository userSearchRepository = profile.getSearchRepository();
                        Map<String, ? extends WebSearchable> userWsMap =
                            userSearchRepository.getWebSearchableMap(type);
                        Map<String, ? extends WebSearchable> globalWsMap =
                            globalRepository.getWebSearchableMap(type);
                        wsMap = new HashMap<String, WebSearchable>(userWsMap);
                        wsMap.putAll(globalWsMap);
                    }
                }
            }


            Map<String, ? extends WebSearchable> filteredWsMap
                                    = new LinkedHashMap<String, WebSearchable>();
            //Filter by aspects (defined in superuser account)
            List<String> aspectTags = new ArrayList<String>();
            List<String> userTags = new ArrayList<String>();
            for (String tag :tags) {
                if (tag.startsWith(TagNames.IM_ASPECT_PREFIX)) {
                    aspectTags.add(tag);
                } else {
                    userTags.add(tag);
                }
            }
            if (aspectTags.size() > 0) {
                wsMap = new SearchFilterEngine().filterByTags(wsMap, aspectTags, type,
                                                              pm.getSuperuser(), getTagManager());
            }

            if (profile.getUsername() != null && userTags.size() > 0) {
                filteredWsMap = new SearchFilterEngine().filterByTags(wsMap, userTags, type,
                        profile.getUsername(), getTagManager());
            } else {
                filteredWsMap = wsMap;
            }

            List returnList = new ArrayList<String>();

            returnList.add(callId);

            // We need a modifiable map so we can filter out invalid templates
            LinkedHashMap<String, ? extends WebSearchable> modifiableWsMap =
                new LinkedHashMap(filteredWsMap);

            SearchRepository.filterOutInvalidTemplates(modifiableWsMap);
            for (WebSearchable ws: modifiableWsMap.values()) {
                List row = new ArrayList();
                row.add(ws.getName());
                if (filterText != null && filterText.length() > 1) {
                    row.add(highlightedDescMap.get(ws));
                    row.add(hitMap.get(ws));
                } else {
                    row.add(ws.getDescription());
                }
                returnList.add(row);
            }

            return returnList;
        } catch (RuntimeException e) {
            processException(e);
            return null;
        }
    }

    /**
     * For a given bag name and a type different from the bag type, give the number of
     * converted objects
     *
     * @param bagName the name of the bag
     * @param type the type to convert to
     * @return the number of converted objects
     */
    public static int getConvertCountForBag(String bagName, String type) {
        try {
            ServletContext servletContext = WebContextFactory.get().getServletContext();
            HttpSession session = WebContextFactory.get().getSession();
            ObjectStore os = (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE);
            String pckName =  os.getModel().getPackageName();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            BagManager bagManager = SessionMethods.getBagManager(servletContext);

            InterMineBag imBag = null;
            int count = 0;
            try {
                imBag = bagManager.getUserOrGlobalBag(profile, bagName);
                Map<String, QuerySelectable> pathToQueryNode = new HashMap();
                Map<String, InterMineBag> bagMap = new HashMap<String, InterMineBag>();
                bagMap.put(imBag.getName(), imBag);

                ProfileManager pm =
                    (ProfileManager) servletContext.getAttribute(Constants.PROFILE_MANAGER);
                PathQuery pathQuery = TypeConverter.getConversionQuery(TypeConverterHelper.
                    getConversionTemplates(pm.getSuperuserProfile()),
                    TypeUtil.instantiate(pckName + "." + imBag.getType()),
                    TypeUtil.instantiate(pckName + "." + type), imBag);
                Query query = QueryCreationHelper.makeQuery(pathQuery, bagMap, pathToQueryNode,
                    pm, null, false,
                    (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE),
                    getClassKeys(servletContext),
                    (BagQueryConfig) servletContext.getAttribute(Constants.BAG_QUERY_CONFIG));
                count = os.count(query, ObjectStore.SEQUENCE_IGNORE);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return count;
        } catch (RuntimeException e) {
            processException(e);
            return 0;
        }
    }

    /**
     * Saves information, that some element was toggled - displayed or hidden.
     *
     * @param elementId element id
     * @param opened new aspect state
     */
    public static void saveToggleState(String elementId, boolean opened) {
        try {
            AjaxServices.getWebState().getToggledElements().put(elementId,
                    Boolean.valueOf(opened));
        } catch (RuntimeException e) {
            processException(e);
        }
    }

    /**
     * Set state that should be saved during the session.
     * @param name name of state
     * @param value value of state
     */
    public static void setState(String name, String value) {
        try {
            AjaxServices.getWebState().setState(name, value);
        } catch (RuntimeException e) {
            processException(e);
        }
    }

    /**
     * Get state.
     * @param name name of state
     * @return value if state was set before during the session else null
     */
    public static String getState(String name) {
        try {
            return (String) AjaxServices.getWebState().getState(name);
        } catch (RuntimeException e) {
            processException(e);
        }
        return null;
    }

    /**
     * validate bag upload
     * @param bagName name of new bag to be validated
     * @return error msg to display, if any
     */
    public static String validateBagName(String bagName) {

        try {
            ServletContext servletContext = WebContextFactory.get().getServletContext();
            HttpSession session = WebContextFactory.get().getSession();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            BagManager bagManager = SessionMethods.getBagManager(servletContext);
            
            // TODO get message text from the properties file
            if (bagName.equals("")) {
                return "You cannot save a list with a blank name";
            }

            if (!NameUtil.isValidName(bagName)) {
                return INVALID_NAME_MSG;
            }

            if (profile.getSavedBags().get(bagName) != null) {
                return "The list name you have chosen is already in use";
            }

            if (bagManager.getGlobalBag(bagName) != null) {
                return "The list name you have chosen is already in use -"
                + " there is a public list called " + bagName;
            }

            return "";
        } catch (RuntimeException e) {
            processException(e);
            return null;
        }
    }

    /**
     * validation that happens before new bag is saved
     * @param bagName name of new bag
     * @param selectedBags bags involved in operation
     * @param operation which operation is taking place - delete, union, intersect or subtract
     * @return error msg, if any
     */
    public static String validateBagOperations(String bagName, String[] selectedBags,
                                               String operation) {

        try {
            ServletContext servletContext = WebContextFactory.get().getServletContext();
            HttpSession session = WebContextFactory.get().getSession();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);

            // TODO get error text from the properties file
            if (selectedBags.length == 0) {
                return "No lists are selected";
            }
            if (operation.equals("delete")) {
                for (int i = 0; i < selectedBags.length; i++) {
                    Set<String> queries = new HashSet<String>();
                    queries.addAll(queriesThatMentionBag(profile.getSavedQueries(),
                                                         selectedBags[i]));
                    queries.addAll(queriesThatMentionBag(profile.getHistory(),
                                                         selectedBags[i]));
                    if (queries.size() > 0) {
                        return "List " + selectedBags[i] + " cannot be deleted as it is referenced "
                        + "by other queries " + queries;
                    }
                }
            } else {
                Properties properties = (Properties)
                servletContext.getAttribute(Constants.WEB_PROPERTIES);
                String defaultName = properties.getProperty("lists.input.example");
                if (!operation.equals("copy") && (bagName.equals("")
                                || (bagName.equalsIgnoreCase(defaultName)))) {
                    return "New list name is required";
                } else if (!NameUtil.isValidName(bagName)) {
                    return INVALID_NAME_MSG;
                }
            }
            return "";
        } catch (RuntimeException e) {
            processException(e);
            return null;
        }
    }

    /**
     * Provide a list of queries that mention a named bag
     * @param savedQueries a saved queries map (name -&gt; query)
     * @param bagName the name of a bag
     * @return the list of queries
     */
    public static List<String> queriesThatMentionBag(Map savedQueries, String bagName) {
        try {
            List<String> queries = new ArrayList<String>();
            for (Iterator i = savedQueries.keySet().iterator(); i.hasNext();) {
                String queryName = (String) i.next();
                SavedQuery query = (SavedQuery) savedQueries.get(queryName);
                if (query.getPathQuery().getBagNames().contains(bagName)) {
                    queries.add(queryName);
                }
            }
            return queries;
        } catch (RuntimeException e) {
            processException(e);
            return null;
        }
    }

    /**
     * @param widgetId unique id for this widget
     * @param bagName name of list
     * @param selectedExtraAttribute extra attribute (like organism)
     * @return graph widget
     */
    public static GraphWidget getProcessGraphWidget(String widgetId, String bagName,
                                                    String selectedExtraAttribute) {
        try {
            ServletContext servletContext = WebContextFactory.get().getServletContext();
            HttpSession session = WebContextFactory.get().getSession();
            WebConfig webConfig = (WebConfig) servletContext.getAttribute(Constants.WEBCONFIG);
            ObjectStore os = (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE);
            Model model =  os.getModel();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            BagManager bagManager = SessionMethods.getBagManager(servletContext);
            InterMineBag imBag = bagManager.getUserOrGlobalBag(profile, bagName);

            Type type = webConfig.getTypes().get(model.getPackageName()
                            + "." + imBag.getType());
            List<WidgetConfig> widgets = type.getWidgets();
            for (WidgetConfig widget: widgets) {
                if (widget.getId().equals(widgetId)) {
                    GraphWidgetConfig graphWidgetConf = (GraphWidgetConfig) widget;
                    graphWidgetConf.setSession(session);
                    GraphWidget graphWidget = new GraphWidget(graphWidgetConf, imBag, os,
                                    selectedExtraAttribute);
                    return graphWidget;
                }
            }
        } catch (RuntimeException e) {
            processException(e);
        }
        return null;
    }

    /**
     * @param widgetId unique id for this widget
     * @param bagName name of list
     * @return graph widget
     */
    public static HTMLWidget getProcessHTMLWidget(String widgetId, String bagName) {
        try {
            ServletContext servletContext = WebContextFactory.get().getServletContext();
            HttpSession session = WebContextFactory.get().getSession();
            WebConfig webConfig = (WebConfig) servletContext.getAttribute(Constants.WEBCONFIG);
            Model model = (Model) servletContext.getAttribute(Constants.MODEL);
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);

            BagManager bagManager = SessionMethods.getBagManager(servletContext);
            InterMineBag imBag = bagManager.getUserOrGlobalBag(profile, bagName);

            Type type = webConfig.getTypes().get(model.getPackageName()
                            + "." + imBag.getType());
            List<WidgetConfig> widgets = type.getWidgets();
            for (WidgetConfig widget: widgets) {
                if (widget.getId().equals(widgetId)) {
                    HTMLWidgetConfig htmlWidgetConf = (HTMLWidgetConfig) widget;
                    HTMLWidget htmlWidget = new HTMLWidget(htmlWidgetConf);
                    return htmlWidget;
                }
            }
        } catch (RuntimeException e) {
            processException(e);
        }
        return null;
    }
    
    /**
     *
     * @param widgetId unique ID for this widget
     * @param bagName name of list
     * @return table widget
     */
    public static TableWidget getProcessTableWidget(String widgetId, String bagName) {
        try {
            ServletContext servletContext = WebContextFactory.get().getServletContext();
            HttpSession session = WebContextFactory.get().getSession();
            WebConfig webConfig = (WebConfig) servletContext.getAttribute(Constants.WEBCONFIG);
            ObjectStore os = (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE);
            Model model =  os.getModel();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            BagManager bagManager = SessionMethods.getBagManager(servletContext);
            InterMineBag imBag = bagManager.getUserOrGlobalBag(profile, bagName);
            Map classKeys = getClassKeys(servletContext);

            Type type = webConfig.getTypes().get(model.getPackageName()
                            + "." + imBag.getType());
            List<WidgetConfig> widgets = type.getWidgets();
            for (WidgetConfig widgetConfig: widgets) {
                if (widgetConfig.getId().equals(widgetId)) {
                    TableWidgetConfig tableWidgetConfig = (TableWidgetConfig) widgetConfig;
                    tableWidgetConfig.setClassKeys(classKeys);
                    tableWidgetConfig.setWebConfig(webConfig);
                    TableWidget tableWidget = new TableWidget(tableWidgetConfig, imBag, os, null);
                    return tableWidget;
                }
            }
        } catch (RuntimeException e) {
            processException(e);
        } 
        return null;
    }

    /**
     *
     * @param widgetId unique ID for each widget
     * @param bagName name of list
     * @param errorCorrection error correction method to use
     * @param max maximum value to display
     * @param filters list of strings used to filter widget results, ie Ontology
     * @param externalLink link to external datasource
     * @param externalLinkLabel name of external datasource.
     * @return enrichment widget
     */
    public static EnrichmentWidget getProcessEnrichmentWidget(String widgetId, String bagName,
                                                              String errorCorrection, String max,
                                                              String filters,
                                                              String externalLink,
                                                              String externalLinkLabel) {
        try {
            ServletContext servletContext = WebContextFactory.get().getServletContext();
            HttpSession session = WebContextFactory.get().getSession();
            WebConfig webConfig = (WebConfig) servletContext.getAttribute(Constants.WEBCONFIG);
            ObjectStore os = (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE);
            Model model = os.getModel();
            Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
            BagManager bagManager = SessionMethods.getBagManager(servletContext);

            InterMineBag imBag = bagManager.getUserOrGlobalBag(profile, bagName);
            Type type = webConfig.getTypes().get(model.getPackageName()
                    + "." + imBag.getType());
            List<WidgetConfig> widgets = type.getWidgets();
            for (WidgetConfig widgetConfig : widgets) {
                if (widgetConfig.getId().equals(widgetId)) {
                    EnrichmentWidgetConfig enrichmentWidgetConfig =
                                                        (EnrichmentWidgetConfig) widgetConfig;
                    enrichmentWidgetConfig.setExternalLink(externalLink);
                    enrichmentWidgetConfig.setExternalLinkLabel(externalLinkLabel);
                    EnrichmentWidget enrichmentWidget = new EnrichmentWidget(
                                    enrichmentWidgetConfig, imBag, os, filters, max,
                                    errorCorrection);
                    return enrichmentWidget;
                }
            }
        } catch (RuntimeException e) {
            processException(e);
        }
        return null;
    }

    /**
    *
    * @param widgetId unique ID for each widget
    * @param bagName name of list
    * @param highlight for highlighting
    * @param pValue pValue
    * @param numberOpt numberOpt
    * @param externalLink link to external datasource
    * @param externalLinkLabel name of external datasource.
    * @return enrichment widget
    */
   public static GridWidget getProcessGridWidget(String widgetId, String bagName,
                                                             String highlight,
                                                             String pValue,
                                                             String numberOpt,
                                                             String externalLink,
                                                             String externalLinkLabel) {
       try {
           ServletContext servletContext = WebContextFactory.get().getServletContext();
           HttpSession session = WebContextFactory.get().getSession();
           WebConfig webConfig = (WebConfig) servletContext.getAttribute(Constants.WEBCONFIG);
           ObjectStore os = (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE);
           Model model = os.getModel();
           Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
           BagManager bagManager = SessionMethods.getBagManager(servletContext);

           InterMineBag imBag = bagManager.getUserOrGlobalBag(profile, bagName);
           Type type = webConfig.getTypes().get(model.getPackageName()
                   + "." + imBag.getType());
           List<WidgetConfig> widgets = type.getWidgets();
           for (WidgetConfig widgetConfig : widgets) {
               if (widgetConfig.getId().equals(widgetId)) {
                   GridWidgetConfig gridWidgetConfig =
                                                       (GridWidgetConfig) widgetConfig;
                   gridWidgetConfig.setExternalLink(externalLink);
                   gridWidgetConfig.setExternalLinkLabel(externalLinkLabel);
                   GridWidget gridWidget = new GridWidget(
                           gridWidgetConfig, imBag, os, null, highlight, pValue, numberOpt);
                   return gridWidget;
               }
           }
       } catch (RuntimeException e) {
           processException(e);
       }
       return null;
   }


    /**
     * Add an ID to the PagedTable selection
     * @param selectedId the id
     * @param tableId the identifier for the PagedTable
     * @param columnIndex the column of the selected id
     * @return the field values of the first selected objects
     */
    public static List<String> selectId(String selectedId, String tableId, String columnIndex) {
        WebContext ctx = WebContextFactory.get();
        HttpSession session = ctx.getSession();
        ServletContext servletContext = ctx.getServletContext();
        PagedTable pt = SessionMethods.getResultsTable(session, tableId);
        pt.selectId(new Integer(selectedId), (new Integer(columnIndex)).intValue());
        Map<String, List<FieldDescriptor>> classKeys = getClassKeys(servletContext);
        ObjectStore os = (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE);
        return pt.getFirstSelectedFields(os, classKeys);
    }

    /**
     * remove an Id from the PagedTable
     * @param deSelectId the ID to remove from the selection
     * @param tableId the PagedTable identifier
     * @return the field values of the first selected objects
     */
    public static List<String> deSelectId(String deSelectId, String tableId) {
        WebContext ctx = WebContextFactory.get();
        HttpSession session = ctx.getSession();
        ServletContext servletContext = ctx.getServletContext();
        PagedTable pt = SessionMethods.getResultsTable(session, tableId);
        pt.deSelectId(new Integer(deSelectId));
        Map<String, List<FieldDescriptor>> classKeys = getClassKeys(servletContext);
        ObjectStore os = (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE);
        return pt.getFirstSelectedFields(os, classKeys);
    }

    /**
     *
     * @param servletContext
     * @return
     */
    @SuppressWarnings("unchecked")
    private static Map<String, List<FieldDescriptor>> getClassKeys(ServletContext servletContext) {
        return (Map) servletContext.getAttribute(Constants.CLASS_KEYS);
    }

    /**
     * Select all the elements in a PagedTable
     * @param index the index of the selected column
     * @param tableId the PagedTable identifier
     */
    public static void selectAll(int index, String tableId) {
        HttpSession session = WebContextFactory.get().getSession();
        PagedTable pt = SessionMethods.getResultsTable(session, tableId);
        pt.clearSelectIds();
        pt.setAllSelectedColumn(index);
    }

    /**
     * AJAX request - reorder view.
     * @param newOrder the new order as a String
     * @param oldOrder the previous order as a String
     */
    public void reorder(String newOrder, String oldOrder) {
        HttpSession session = WebContextFactory.get().getSession();
        List<String> newOrderList =
            new LinkedList<String>(StringUtil.serializedSortOrderToMap(newOrder).values());
        List<String> oldOrderList =
            new LinkedList<String>(StringUtil.serializedSortOrderToMap(oldOrder).values());

        List<Path> view = SessionMethods.getEditingView(session);
        ArrayList<Path> newView = new ArrayList<Path>();

        for (int i = 0; i < view.size(); i++) {
            String newi = newOrderList.get(i);
            int oldi = oldOrderList.indexOf(newi);
            newView.add(view.get(oldi));
        }

        PathQuery pathQuery = (PathQuery) session.getAttribute(Constants.QUERY);
        pathQuery.setViewPaths(newView);
    }

    /**
     * Add a Node from the sort order
     * @param path the Path as a String
     * @param direction the direction to sort by
     * @exception Exception if the application business logic throws
     */
    public void addToSortOrder(String path, String direction)
        throws Exception {
        HttpSession session = WebContextFactory.get().getSession();
        PathQuery query = (PathQuery) session.getAttribute(Constants.QUERY);
        query.setOrderBy(path, direction);
    }

    /**
     * Reset the sort order
     */
    public void clearSortOrder() {
        HttpSession session = WebContextFactory.get().getSession();
        PathQuery query = (PathQuery) session.getAttribute(Constants.QUERY);
        query.resetOrderBy();
    }

    /**
     * Get the news
     * @param rssURI the URI of the rss feed
     * @return the news feed as html
     */
    public static String getNewsRead(String rssURI) {
        try {
            URL feedUrl = new URL(rssURI);
            SyndFeedInput input = new SyndFeedInput();
            XmlReader reader;
            try {
                reader = new XmlReader(feedUrl);
            } catch (Throwable e) {
                // xml document at this url doesn't exist or is invalid, so the news cannot be read
                return "<i>No news</i>";
            }
            SyndFeed feed = input.build(reader);
            List<SyndEntry> entries = feed.getEntries();
            StringBuffer html = new StringBuffer("<ol id=\"news\">");
            int counter = 0;
            for (SyndEntry syndEntry : entries) {
                if (counter > 4) {
                    break;
                }

                // NB: apparently, the only accepted formats for getPublishedDate are
                // Fri, 28 Jan 2008 11:02 GMT
                // or
                // Fri, 8 Jan 2008 11:02 GMT
                // or
                // Fri, 8 Jan 08 11:02 GMT
                //
                // an annoying error appears if the format is not followed, and news tile hangs.
                //
                // the following is used to display the date without timestamp.
                // this should always work since the retrieved date has a fixed format,
                // independent of the one used in the xml.
                // longDate = Wed Aug 19 14:44:19 BST 2009                
                String longDate = syndEntry.getPublishedDate().toString();
                String dayMonth = longDate.substring(0, 10);
                String year = longDate.substring(24);
                
                DateFormat df = new SimpleDateFormat("EEE MMM dd hh:mm:ss zzz yyyy");
                Date date = df.parse(longDate);
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                
                // month starts at zero
                int month = calendar.get(calendar.MONTH) + 1;
                String monthString = String.valueOf(month);
                if (monthString.length() == 1) {
                    monthString = "0" + monthString;
                }
                
                //http://blog.flymine.org/2009/08/
                WebContext ctx = WebContextFactory.get();
                ServletContext servletContext = ctx.getServletContext();
                Properties properties = (Properties)
                servletContext.getAttribute(Constants.WEB_PROPERTIES);
                
                String url = properties.getProperty("project.news") + "/" + year + "/" 
                + monthString;
                
                html.append("<li>");
                html.append("<strong>");
                html.append("<a href=\"" + url + "\">");
                html.append(syndEntry.getTitle());
                html.append("</a>");
                html.append("</strong>");
                html.append(" - <em>" + dayMonth + " " + year + "</em><br/>");
//                html.append("- <em>" + syndEntry.getPublishedDate().toString() + "</em><br/>");
                html.append(syndEntry.getDescription().getValue());
                html.append("</li>");
                counter++;
            }
            html.append("</ol>");
            return html.toString();
        } catch (MalformedURLException e) {
            return "<i>No news at specified URL</i>";
        } catch (IllegalArgumentException e) {
            return "<i>No news at specified URL</i>";
        } catch (FeedException e) {
            return "<i>No news at specified URL</i>";
        } catch (java.text.ParseException e) {
            return "<i>No news at specified URL</i>";
        }
    }

    //*****************************************************************************
    // Tags AJAX Interface
    //*****************************************************************************

    /**
     * Returns all objects names tagged with specified tag type and tag name.
     * @param type tag type
     * @param tag tag name
     * @return objects names
     */
    public static Set<String> filterByTag(String type, String tag) {
        Profile profile = getProfile(getRequest());

        SearchRepository searchRepository = profile.getSearchRepository();
        Map<String, WebSearchable> map = (Map<String, WebSearchable>) searchRepository.
            getWebSearchableMap(type);
        if (map == null) {
            return null;
        }
        Map<String, WebSearchable> filteredMap = new TreeMap<String, WebSearchable>();
        List<String> tagList = new ArrayList<String>();
        tagList.add(tag);
        filteredMap.putAll(new SearchFilterEngine().filterByTags(map, tagList, type,
                profile.getUsername(), getTagManager()));
        return filteredMap.keySet();
    }
    /**
     * Adds tag and assures that there is only one tag for this combination of tag name, tagged
     * Object and type.
     * @param tag tag name
     * @param taggedObject object id that is tagged by this tag
     * @param type  tag type
     * @return 'ok' string if succeeded else error string
     */
    public static String addTag(String tag, String taggedObject, String type) {
        String tagName = tag;
        LOG.info("Called addTag(). tagName:" + tagName + " taggedObject:"
                + taggedObject + " type: " + type);

        try {
            HttpServletRequest request = getRequest();
            Profile profile = getProfile(request);
            tagName = tagName.trim();
            HttpSession session = request.getSession();

            if (profile.getUsername() != null
                    && !StringUtils.isEmpty(tagName)
                    && !StringUtils.isEmpty(type)
                    && !StringUtils.isEmpty(taggedObject)) {
                if (tagExists(tagName, taggedObject, type)) {
                    return "Already tagged with this tag.";
                }
                if (!TagManager.isValidTagName(tagName)) {
                    return INVALID_NAME_MSG;
                }
                if (tagName.startsWith(TagNames.IM_PREFIX)
                        && !SessionMethods.isSuperUser(session)) {
                    return "You cannot add a tag starting with " + TagNames.IM_PREFIX + ", "
                        + "that is a reserved word.";
                }

                TagManager tagManager = getTagManager();
                tagManager.addTag(tagName, taggedObject, type, profile.getUsername());

                ServletContext servletContext = session.getServletContext();
                if (SessionMethods.isSuperUser(session)) {
                    SearchRepository tr = SessionMethods.
                        getGlobalSearchRepository(servletContext);
                    tr.webSearchableTagChange(type, tagName);
                }
                return "ok";
            }
            return "Adding tag failed.";
        } catch (Throwable e) {
            LOG.error("Adding tag failed", e);
            return "Adding tag failed.";
        }
    }

    /**
     * Deletes tag.
     * @param tagName tag name
     * @param tagged id of tagged object
     * @param type tag type
     * @return 'ok' string if succeeded else error string
     */
    public static String deleteTag(String tagName, String tagged, String type) {
        LOG.info("Called deleteTag(). tagName:" + tagName + " taggedObject:"
                + tagged + " type: " + type);
        try {
            HttpServletRequest request = getRequest();
            Profile profile  = getProfile(request);
            TagManager manager = SessionMethods.getTagManager(request.getSession());
            manager.deleteTag(tagName, tagged, type, profile.getUsername());
            HttpSession session = request.getSession();
            ServletContext servletContext = session.getServletContext();
            if (SessionMethods.isSuperUser(session)) {
                SearchRepository tr =
                    SessionMethods.getGlobalSearchRepository(servletContext);
                tr.webSearchableTagChange(type, tagName);
            }
            return "ok";
        } catch (Throwable e) {
            LOG.error("Deleting tag failed", e);
            return "Deleting tag failed.";
        }
    }

    /**
     * Returns all tags of specified tag type together with prefixes of these tags.
     * For instance: for tag 'bio:experiment' it automatically adds 'bio' tag.
     * @param type tag type
     * @return tags
     */
    public static Set<String> getTags(String type) {
        HttpServletRequest request = getRequest();
        TagManager tagManager = SessionMethods.getTagManager(request.getSession());
        Profile profile = getProfile(request);
        if (profile.isLoggedIn()) {
            return tagManager.getUserTagNames(type, profile.getUsername());
        }
        return new TreeSet<String>();
    }

    /**
     * Returns all tags by which is specified object tagged.
     * @param type tag type
     * @param tagged id of tagged object
     * @return tags
     */
    public static Set<String> getObjectTags(String type, String tagged) {
        HttpServletRequest request = getRequest();
        TagManager tagManager = SessionMethods.getTagManager(request.getSession());
        Profile profile = getProfile(request);
        if (profile.isLoggedIn()) {
            return tagManager.getObjectTagNames(tagged, type, profile.getUsername());
        }
        return new TreeSet<String>();
    }

    private static boolean tagExists(String tag, String taggedObject, String type) {
        HttpServletRequest request = getRequest();
        TagManager tagManager = SessionMethods.getTagManager(request.getSession());
        String userName = getProfile(request).getUsername();
        return tagManager.getObjectTagNames(taggedObject, type, userName).contains(tag);
    }


    private static Profile getProfile(HttpServletRequest request) {
        return (Profile) request.getSession().getAttribute(Constants.PROFILE);
    }

    private static HttpServletRequest getRequest() {
        return WebContextFactory.get().getHttpServletRequest();
    }

    private static TagManager getTagManager() {
        return SessionMethods.getTagManager(getRequest().getSession());
    }

    /**
     * Set the constraint logic on a query to be the given expression
     * @param expression the constraint logic for the query
     */
    public static void setConstraintLogic(String expression) {
        WebContext ctx = WebContextFactory.get();
        HttpSession session = ctx.getSession();
        PathQuery query = (PathQuery) session.getAttribute(Constants.QUERY);
        query.setConstraintLogic(expression);
        query.syncLogicExpression(SessionMethods.getDefaultOperator(session));
    }

    /**
     * Get the grouped constraint logic
     * @return a list representing the grouped constraint logic
     */
    public static String getConstraintLogic() {
        WebContext ctx = WebContextFactory.get();
        HttpSession session = ctx.getSession();
        PathQuery query = (PathQuery) session.getAttribute(Constants.QUERY);
        return (query.getGroupedConstraintLogic().toString());
    }

    public String[] getContent(String suffix, boolean wholeList, String field, String className) {
        ServletContext servletContext = WebContextFactory.get().getServletContext();
        AutoCompleter ac = (AutoCompleter) servletContext.getAttribute(Constants.AUTO_COMPLETER);
        ac.createRAMIndex(className + "." + field);
        if (!wholeList && suffix.length() > 0) {
            String[] shortList = ac.getFastList(suffix, field, 31);
            return shortList;
        } else if (suffix.length() > 2 && wholeList) {
            String[] longList = ac.getList(suffix, field);
            return longList;
        }
        String[] defaultList = {""};
        return defaultList;
    }

}
