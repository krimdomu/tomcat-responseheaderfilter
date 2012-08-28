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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

public class DefaultMappingProcessor implements MappingProcessor {
  public void preProcess(HttpServletRequest request, HttpServletResponse response, Rule applicableRule) {
  }

  public void applyHeaders(HttpServletRequest request,
                           HttpServletResponse response, 
                           List<Rule.ResponseHeader> responseHeaders,
                           Rule applicableRule) {
    for(Rule.ResponseHeader responseHeader : responseHeaders){
      response.setHeader(responseHeader.getResponseHeaderKey(), responseHeader.getResponseHeaderValue());
    }
  }

  public void postProcess(HttpServletRequest request, HttpServletResponse response, Rule applicableRule) {
  }
}