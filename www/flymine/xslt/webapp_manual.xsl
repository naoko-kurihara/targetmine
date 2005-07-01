<?xml version='1.0' encoding="utf-8"?>
<xsl:stylesheet
   xmlns="http://www.w3.org/1999/xhtml"
   xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   version='1.0'>

  <xsl:import href="../../xslt/ulink.xsl"/>
  <xsl:import href="../../xslt/menu.xsl"/>
  <xsl:import href="../../xslt/page_template.xsl"/>

  <xsl:param name="outputext"/>
  <xsl:param name="basedir"/>
  <xsl:param name="branding"/>
  <xsl:param name="webappprefix"/>
  <xsl:param name="sourceref"/>

  <!-- copied so that we can add header, sidebar and pagecontent DIVs -->
  <xsl:template name="chunk-element-content">
    <xsl:param name="prev"/>
    <xsl:param name="next"/>
    <xsl:param name="nav.context"/>
    <xsl:param name="content">
      <xsl:apply-imports/>
    </xsl:param>

    <xsl:call-template name="user.preroot"/>

    <html>
      <xsl:call-template name="html.head">
        <xsl:with-param name="prev" select="$prev"/>
        <xsl:with-param name="next" select="$next"/>
      </xsl:call-template>

      <body>
        <div id="header">
          <h1>
            <a href="{$basedir}">
              <xsl:apply-templates mode="copy-no-ns" select="$brand/title/node()"/>
            </a>
          </h1>
          <p>
            <xsl:apply-templates mode="copy-no-ns" select="$brand/headline/node()"/>
          </p>
        </div>

        <div id="pagecontent">
          <table id="static-table" width="100%" cellpadding="0" >
            <tr>
              <td id="sidebar" valign="top" width="15%">
                <xsl:call-template name="sidebar"/>
              </td>
              <td id="static-content" valign="top" width="85%">
                <xsl:call-template name="body.attributes"/>
                <xsl:call-template name="user.header.navigation"/>

                <xsl:call-template name="header.navigation">
	          <xsl:with-param name="prev" select="$prev"/>
	          <xsl:with-param name="next" select="$next"/>
	          <xsl:with-param name="nav.context" select="$nav.context"/>
                </xsl:call-template>

                <xsl:call-template name="user.header.content"/>

                <xsl:copy-of select="$content"/>

                <xsl:call-template name="user.footer.content"/>

                <xsl:call-template name="footer.navigation">
	          <xsl:with-param name="prev" select="$prev"/>
	          <xsl:with-param name="next" select="$next"/>
	          <xsl:with-param name="nav.context" select="$nav.context"/>
                </xsl:call-template>

                <xsl:call-template name="user.footer.navigation"/>
              </td>
            </tr>
          </table>
        </div>
      </body>
    </html>
  </xsl:template>

  <!-- inserted in the HEAD element -->
  <xsl:template name="user.head.content">
    <xsl:for-each select="$brand/stylesheet">
      <link rel="stylesheet" type="text/css" href="{concat($basedir, '/', @file)}"
            media="screen,printer"/>
    </xsl:for-each>
  </xsl:template>

  <xsl:template name="gentext.nav.home">
    Table of contents
  </xsl:template>
</xsl:stylesheet>
