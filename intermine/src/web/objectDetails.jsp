<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<!-- objectDetails.jsp -->
Details for
<c:forEach items="${object.clds}" var="cld">
  <b>${cld.unqualifiedName}</b>
</c:forEach>
object<br/><br/>

Identifiers<br/>
<table style="padding-left: 20px" cellspacing="10">
  <c:forEach items="${object.identifiers}" var="entry">
    <tr>
      <td valign="top"><b>${entry.key}</b></td>
      <td>${entry.value}</td>
    </tr>
  </c:forEach>
</table>

<br/>

Attributes<br/>
<table style="padding-left: 20px" cellspacing="10">
  <c:forEach items="${object.attributes}" var="entry">
    <tr>
      <td valign="top"><b>${entry.key}</b></td>
      <td>${entry.value}</td>
    </tr>
  </c:forEach>
</table>
<br/>

<c:if test="${!empty object.references}">
  References<br/>
  <table style="padding-left: 20px" cellspacing="10">
    <c:forEach items="${object.references}" var="entry">
      <tr>
        <td valign="top"><b>${entry.key}</b></td>
        <td>
          <c:set var="reference" value="${entry.value}"/>
          <c:choose>
            <c:when test="${reference.verbose}">
              <html:link action="/modifyDetails?method=unverbosify&field=${entry.key}">
                <img border="0" src="images/minus.png" alt="-"/>
              </html:link>
            </c:when>
            <c:otherwise>
              <html:link action="/modifyDetails?method=verbosify&field=${entry.key}">
                <img border="0" src="images/plus.png" alt="+"/>
              </html:link>
            </c:otherwise>
          </c:choose>
          <c:forEach items="${reference.clds}" var="cld">
            ${cld.unqualifiedName}
          </c:forEach>
          object
          [<html:link action="/objectDetails?id=${reference.id}">
             details...
           </html:link>]<br/>
          <c:if test="${reference.verbose}">
            <c:forEach items="${reference.identifiers}" var="entry">
              &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;${entry.key}=${entry.value}<br/>
            </c:forEach>
          </c:if>
        </td>
      </tr>
    </c:forEach>
  </table>
  <br/>
</c:if>

<c:if test="${!empty object.collections}">
  Collections<br/>
  <table style="padding-left: 20px" cellspacing="10">
    <c:forEach items="${object.collections}" var="entry">
      <c:set var="collection" value="${entry.value}"/>
      <tr>
        <td valign="top"><b>${entry.key}</b></td>
        <td>
          <c:choose>
            <c:when test="${collection.verbose}">
              <html:link action="/modifyDetails?method=unverbosify&field=${entry.key}">
                <img border="0" src="images/minus.png" alt="-"/>
              </html:link>
            </c:when>
            <c:when test="${fn:length(collection.classes) > 0}">
              <html:link action="/modifyDetails?method=verbosify&field=${entry.key}">
                <img border="0" src="images/plus.png" alt="+"/>
              </html:link>
            </c:when>
            <c:otherwise>
              <img border="0" src="images/blank.png" alt="+"/>
            </c:otherwise>
          </c:choose>
          ${collection.size} ${collection.cld.unqualifiedName} object(s)
          [<html:link action="/collectionDetails?id=${object.id}&field=${entry.key}">
                 details...
               </html:link>]<br/>
          <c:if test="${collection.verbose and fn:length(collection.classes) > 0}">
            <c:forEach items="${collection.classes}" var="entry2" varStatus="status">
              &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;${entry2.value}
              <c:forEach items="${entry2.key}" var="cld">
                ${cld.unqualifiedName}
              </c:forEach>
              object(s)
              [<html:link action="/modifyDetails?method=filter&field=${entry.key}&index=${status.index}">
                 details...
               </html:link>]<br/>
            </c:forEach>
          </c:if>
        </td>
      </tr>
    </c:forEach>
  </table>
  <br/>
</c:if>

<html:link action="/changeResults?method=reset">
  <fmt:message key="results.return"/>
</html:link>
<!-- /objectDetails.jsp -->
