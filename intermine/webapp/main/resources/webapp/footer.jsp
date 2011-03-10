<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<!-- footer.jsp -->

<br/><br/><br/>

<div class="body" align="center" style="clear:both">

      <c:if test="${pageName != 'contact'}">
        <div id="contactFormDivButton">
          <im:vspacer height="11"/>
          <div class="contactButton">
             <a href="#" onclick="showContactForm();return false">
               <b><fmt:message key="feedback.title"/></b>
             </a>
          </div>
        </div>

      <div id="contactFormDiv" style="display:none;">
            <im:vspacer height="11"/>
              <tiles:get name="contactForm"/>
        </div>
      </c:if>

<br/>
<div id="funding-footer">
	<!-- <fmt:message key="funding"/> -->
	<p>Powered by</p>
	<a target="new" href="http://intermine.org" title="InterMine">
		<img src="images/icons/intermine-footer-logo.png" alt="InterMine logo" />
	</a>
<!-- chenyian: NIBIO -->
 <br/><br/>
 <div>TargetMine is funded by <a href="http://www.nedo.go.jp/" target="_new" 
 	title="New Energy and Industrial Technology Development Organization">NEDO</a>. 
 	Developed by <a href="http://mizuguchilab.org/" target="_new">The Mizuguchi Laboratory</a>. 
 	<a href="http://www.nibio.go.jp/" target="_new" title="National Institute of Biomedical Innovation">
 	<img src="/targetmine/model/images/logo_nibio_full.png" border="0" alt="NIBIO_logo"></a>
 </div>

</div>

</div>

<!-- /footer.jsp -->
