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

import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;

/**
 * Each &lt;mapping&gt; definition in the <code>configFile</code> is converted into a {@link Rule}
 * <br/>
 * For a sample rule, click here - {@link ConfigProcessor}
 * <br/>
 * For parsing rules, click here - {@link ConfigProcessor#processConfig()}
 * @see ConfigProcessor
 * @see ResponseHeaderManagerFilter
 */
public class Rule {
  private MappingProcessor processorClass;
  private Pattern url;
  private List<ResponseHeader> defaultResponseHeaders;
  private Map<Condition, List<ResponseHeader>> conditionalResponseHeaders;

  /**
   * Comprises of parsed values for a &lt;conditional&gt; tag <br/>
   * For parsing rules, click here - {@link ConfigProcessor#getCondition(org.w3c.dom.Node)}
   *
   * @see Rule
   * @see ResponseHeader
   * @see ConfigProcessor
   * @see ResponseHeaderManagerFilter
   */
  public static class Condition {
    private String queryParamName;
    private Pattern queryParamValue;

    public String getQueryParamName() {
      return queryParamName;
    }

    public void setQueryParamName(String queryParamName) {
      this.queryParamName = queryParamName;
    }

    public Pattern getQueryParamValue() {
      return queryParamValue;
    }

    public void setQueryParamValue(Pattern queryParamValue) {
      this.queryParamValue = queryParamValue;
    }
  }

  /**
   * Comprises of parsed values for &lt;header&gt; nodes inside a &lt;response-headers&gt; tag<br/>
   * For parsing rules, click here - {@link ConfigProcessor#getResponseHeader(org.w3c.dom.Node)}
   *
   * @see Rule
   * @see Condition
   * @see ConfigProcessor
   * @see ResponseHeaderManagerFilter
   */
  public static class ResponseHeader {
    private String responseHeaderKey;
    private String responseHeaderValue;

    public String getResponseHeaderKey() {
      return responseHeaderKey;
    }

    public void setResponseHeaderKey(String responseHeaderKey) {
      this.responseHeaderKey = responseHeaderKey;
    }

    public String getResponseHeaderValue() {
      return responseHeaderValue;
    }

    public void setResponseHeaderValue(String responseHeaderValue) {
      this.responseHeaderValue = responseHeaderValue;
    }
  }

  public MappingProcessor getProcessorClass() {
    return processorClass;
  }

  public void setProcessorClass(MappingProcessor processorClass) {
    this.processorClass = processorClass;
  }

  public Pattern getUrl() {
    return url;
  }

  public void setUrl(Pattern url) {
    this.url = url;
  }

  public List<ResponseHeader> getDefaultResponseHeaders() {
    return defaultResponseHeaders;
  }

  public void setDefaultResponseHeaders(List<ResponseHeader> defaultResponseHeaders) {
    this.defaultResponseHeaders = defaultResponseHeaders;
  }

  public Map<Condition, List<ResponseHeader>> getConditionalResponseHeaders() {
    return conditionalResponseHeaders;
  }

  public void setConditionalResponseHeaders(Map<Condition, List<ResponseHeader>> conditionalResponseHeaders) {
    this.conditionalResponseHeaders = conditionalResponseHeaders;
  }
}
