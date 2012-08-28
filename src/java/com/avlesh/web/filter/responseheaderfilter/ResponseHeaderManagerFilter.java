/**
 * Copyright 2009 Avlesh Singh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.avlesh.web.filter.responseheaderfilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.lang.StringUtils;

/**
 * <code>ResponseHeaderManagerFilter</code> is a Java Web Filter for any J2EE compliant web application server
 * (such as Resin or Tomcat), which allows to transparently set response headers.
 * <br/><br/>
 * Some of the most commonly used response headers that this filter can apply are:
 * <ol>
 *  <li><b>Cache-Control</b>: Caching instructions for the client and proxies.</li>
 *  <li><b>Content-Type</b>: The mime type of the response.</li>
 *  <li><b>Content-Encoding</b>: Type of encoding used in the response.</li>
 *  <li><b>Content-Length</b>: Length of the response body in bytes.</li>
 *  <li><b>Expires</b>: Date/time after which the response is considered stale.</li>
 *  <li><b>Set-Cookie</b>: Cookies are headers too. (e.g 	<code>Set-Cookie: foo=moo; Max-Age=3600;</code>)
 * </ol>
 *
 * This filter performs two main tasks:
 * <ol>
 *  <li>Implements the rules ({@link Rule}) obtained
 *  using {@link ConfigProcessor#getRuleMap()}.
 *  </li>
 *  <li>Based on the {@link ConfReloadInfo}, trigger a reload of the configuration if the
 *  <code>configFile</code> gets modified.
 *  </li>
 * </ol>
 *
 * For filter processing rules click here - {@link #doFilter(ServletRequest,ServletResponse,FilterChain)}
 *
 * @see ConfigProcessor
 * @see ConfigProcessor#processConfig()
 */
public class ResponseHeaderManagerFilter implements Filter {
  private static Log logger = LogFactory.getLog(ResponseHeaderManagerFilter.class);

  //default config file
  private String configFileName = "/WEB-INF/response-header-manager.xml";
  private File configFile;

  //reload parameters
  private ConfReloadInfo confReloadInfo;

  //map of rules; is updated accored to rules specified in the confReloadInfo
  private static Map<Pattern, Rule> rules = new ConcurrentHashMap<Pattern, Rule>();
  private static List<Pattern> urlPatterns = new ArrayList<Pattern>();
  
  public void init(FilterConfig filterConfig) throws ServletException, RuntimeException {
    //if specified in web.xml, take that value as the config file
    if(StringUtils.isNotEmpty(filterConfig.getInitParameter("configFile"))){
      configFileName = filterConfig.getInitParameter("configFile");
    }

    String fullConfigFilePath = filterConfig.getServletContext().getRealPath(configFileName);
    configFile = new File(fullConfigFilePath);
    if(!configFile.exists() || !configFile.canRead()){
      //not expecting this, the config file should exist and be readable
      throw new RuntimeException("Cannot initialize ResponseHeaderManagerFilter, error reading " + configFileName);
    }

    //object to hold preferences related to conf reloading
    confReloadInfo = new ConfReloadInfo();
    String reloadCheckIntervalStr = filterConfig.getInitParameter("reloadCheckInterval");
    //if web.xml filter definition has no "reloadCheckInterval" applied, default values in ConfReloadInfo are used  
    if(StringUtils.isNotEmpty(reloadCheckIntervalStr)){
      Integer reloadCheckInterval = Integer.valueOf(reloadCheckIntervalStr);
      if(reloadCheckInterval > 0){
        confReloadInfo.reloadEnabled = true;
        confReloadInfo.reloadCheckInterval = reloadCheckInterval;
      }else{
        //zero or negative values means don't ever reload
        confReloadInfo.reloadEnabled = false;
        confReloadInfo.reloadCheckInterval = 0;
      }
    }

    //parse all the mappings into Rules
    ConfigProcessor configProcessor = new ConfigProcessor(configFile);
    Map<Pattern, Rule> allRules = configProcessor.getRuleMap();
    urlPatterns.addAll(allRules.keySet());
    rules.putAll(allRules);
  }

  /**
   * Underneath are the rules, which are used to process the response and apply a corresponding {@link Rule}
   * <ol>
   *  <li>The rules are applied on a <code>base uri.</code></li>
   *  <li>First all the conditional rules (if available) for a {@link Rule} are matched.</li>
   *  <li>Matching in #2 is done in the reverse order of declaration (of &lt;conditional&gt; tags)
   *  inside the <code>configFile</code>. <i>Last rule wins</i>.
   *  Matching does not cascade and the matcher breaks when a match is found.
   *  </li>
   *  <li>As long as a parameter is present in the queryString (of the <code>request</code>), the value for that
   *  query parameter is <code>request.getParameter(queryParam)</code>. If this turns out to be <code>null</code>
   *  the value <code>""</code> (empty string) is assigned to the queryParam. This is done to match the
   *  <code>Regex</code> (.*) in the condition. Unavailable queryParams in the <code>request</code> are treated as
   *  value <code>null</code>.
   *  </li>
   *  <li>If none of the conditional rules match with an incoming request, the default rule (if present) is applied.</li>
   * </ol>
   *
   * Besides implementing the above rules, if the {@link ConfReloadInfo} so specifies, this method also
   * invokes the {@link #reloadConfigIfNeeded()}
   */
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {
    if(confReloadInfo.reloadEnabled){
      reloadConfigIfNeeded();
    }

    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    String requestUri = request.getRequestURI();

    //get hold of a URL only if its present as a key in the rules map
    Pattern matchingPatternForThisRequest = getMatchingPattern(requestUri);
    if(matchingPatternForThisRequest != null){
      boolean ruleApplied = false;
      //this is the Rule to be applied
      Rule rulesForThisUri = rules.get(matchingPatternForThisRequest);

      Map<Rule.Condition, List<Rule.ResponseHeader>> conditionalResponseHeaders = rulesForThisUri.getConditionalResponseHeaders();

      //if there are conditional mappings in this rule, try an apply them first
      if(conditionalResponseHeaders != null){
        Rule.Condition[] conditions = new Rule.Condition[conditionalResponseHeaders.size()];
        conditions = conditionalResponseHeaders.keySet().toArray(conditions);

        //do a reverse matching for the conditional mappings
        for(int i=conditions.length-1; i>=0; i--){
          Rule.Condition condition = conditions[i];
          String queryParamName = condition.getQueryParamName();
          String requestParamValue = getRequestParamValue(request, queryParamName);

          //try matching only on query params, if it is present in the requests' queryString
          if(requestParamValue != null){
            Pattern queryParamValue = condition.getQueryParamValue();
            Matcher matcher = queryParamValue.matcher(requestParamValue);
            if(matcher.find()){
              if(logger.isDebugEnabled()){
                String requestUrl = requestUri +
                    (StringUtils.isNotEmpty(request.getQueryString()) ? "?" + request.getQueryString() : "");
                logger.debug("Applying conditional response headers to the request - " + requestUrl);
              }
              applyResponseHeaders(request, response, conditionalResponseHeaders.get(condition), rulesForThisUri);
              ruleApplied = true;
              break;
            }
          }
        }
      }

      //if none of the conditional rules matched; and their is a default mapping in the Rule, apply it
      if(!ruleApplied && rulesForThisUri.getDefaultResponseHeaders() != null){
        if(logger.isDebugEnabled()){
          String requestUrl = requestUri +
              (StringUtils.isNotEmpty(request.getQueryString()) ? "?" + request.getQueryString() : "");
          logger.debug("Applying default response headers to the request - " + requestUrl);
        }
        applyResponseHeaders(request, response, rulesForThisUri.getDefaultResponseHeaders(), rulesForThisUri);
      }
    }

    //continue with the chain in any case
    filterChain.doFilter(request, response);
  }

  /**
   * Matches an incoming url against the available patterns in the {@link Rule} map. Involves an iteration over the map
   * keys for each request. Iterates in a reverse order on the map. <i>Last rule wins</i>.
   *
   * @param requestUri (Incoming request uri)
   * @return Matching pattern in the rules map
   */
  protected Pattern getMatchingPattern(String requestUri){
    for(int i=urlPatterns.size()-1; i>=0; i--){
      Matcher matcher = urlPatterns.get(i).matcher(requestUri);
      if(matcher.find()){
        return urlPatterns.get(i);
      }
    }
    return null;
  }

  /**
   * This method implements the reloading of the <code>configFile</code> based on the {@link ConfReloadInfo} parameters.
   * If <code>confReloadInfo.reloadCheckInterval</code> seconds have passed by since the
   * <code>confReloadInfo.lastReloadCheckPerformedOn</code>, and the <code>configFile</code> has been modified again
   * after <code>confReloadInfo.configFileLastModifiedTimeStamp</code>, a cofiguration reload is triggered.
   *
   * @see ResponseHeaderManagerFilter
   * @see ResponseHeaderManagerFilter#doFilter(ServletRequest, ServletResponse, FilterChain) 
   */
  private void reloadConfigIfNeeded() {
    Long now = System.currentTimeMillis();
    if(
      now - confReloadInfo.lastReloadCheckPerformedOn > confReloadInfo.reloadCheckInterval*1000 &&
      configFile.lastModified() > confReloadInfo.configFileLastModifiedTimeStamp
    ){
      if(logger.isDebugEnabled()){
        logger.debug("Response header manager config file has been modified, reloading now");
      }
      long start = System.currentTimeMillis();
      confReloadInfo.configFileLastModifiedTimeStamp = configFile.lastModified();
      confReloadInfo.lastReloadCheckPerformedOn = now;
      ConfigProcessor configProcessor = new ConfigProcessor(configFile);
      Map<Pattern, Rule> allRules = configProcessor.getRuleMap();

      /**
       * no concurrency issues here; concurrent retrievals in doFilter will merely face a contention untill
       * this operation is completed. See {@link java.util.concurrent.ConcurrentHashMap}
       */
      rules.clear();
      rules.putAll(allRules);
      urlPatterns = new ArrayList<Pattern>(allRules.keySet());

      long end = System.currentTimeMillis();
      if(logger.isDebugEnabled()){
        logger.debug("Reload of Response header manager config file successful. Time taken to reload:" + (end-start) + "ms");
      }
    }
  }

  /**
   * As long as a parameter is present in the queryString (of the <code>request</code>), the value for that
   * query parameter is <code>request.getParameter(queryParam)</code>. If this turns out to be <code>null</code>
   * the value <code>""</code> (empty string) is assigned to the queryParam. This is done to match the
   * <code>Regex</code> (.*) in the condition. Unavailable queryParams in the <code>request</code> are treated as
   * value <code>null</code>
   *
   * @param request (The incoming request)
   * @param queryParamName (Parameter which needs to be looked up)
   * @return String (The "value" of this parameter in the queryString)
   *
   * @see ResponseHeaderManagerFilter
   * @see ResponseHeaderManagerFilter#doFilter(ServletRequest, ServletResponse, FilterChain)
   */
  private static String getRequestParamValue(HttpServletRequest request, String queryParamName){
    String paramValue = null;
    if(request.getParameterMap().containsKey(queryParamName)){
      paramValue = request.getParameter(queryParamName);
      if(paramValue == null){
        paramValue = "";
      }
    }
    return paramValue;
  }

  //applies all the response headers to the servlet response, as specified in the configFile
  private void applyResponseHeaders(HttpServletRequest request,
                                    HttpServletResponse response,
                                    List<Rule.ResponseHeader> responseHeaders,
                                    Rule rulesForThisUri) {
    MappingProcessor processorClass = rulesForThisUri.getProcessorClass();
    processorClass.preProcess(request, response, rulesForThisUri);
    processorClass.applyHeaders(request, response, responseHeaders, rulesForThisUri);
    processorClass.postProcess(request, response, rulesForThisUri);
  }

  public void destroy(){
  }

  /**
   * Captures the <code>configFile</code> reload realated info, as well as the state.
   * <ol>
   * <li><code>reloadEnabled</code>: A derived boolean (based on <code>reloadCheckInterval</code>).</li>
   * <li><code>reloadCheckInterval</code>: Number of seconds after which the check for <code>lastModifiedTime
   * </code> is done. Setting this to zero or less will disable reloading. This parameter can be controlled
   * using a <code>filter-init-param</code> called <code>reloadCheckInterval</code> in your <code>web.xml</code>.</li>
   * <li><code>lastReloadCheckPerformedOn</code>: Time in millis when the last reload check was done.</li>
   * <li><code>configFileLastModifiedTimeStamp</code>: Store <code>lastModifiedTimeStamp</code> of the <code>configFile</code>.</li>
   * </ol>
   *
   * @see ResponseHeaderManagerFilter
   * @see ConfigProcessor
   */
  private class ConfReloadInfo{
    private boolean reloadEnabled = true;
    private Integer reloadCheckInterval = 10;
    private Long lastReloadCheckPerformedOn = 0l;
    private Long configFileLastModifiedTimeStamp = 0l;
  }
}