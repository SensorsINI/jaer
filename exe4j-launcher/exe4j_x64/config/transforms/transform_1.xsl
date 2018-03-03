<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="">

  <xsl:output indent="yes"/>

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="/exe4j/application">
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <languages>
        <principalLanguage id="{translate(substring(string(/exe4j/executable/messageSet/@language), 1, 2), 'EDF', 'edf')}"/>
      </languages>
    </xsl:copy>

  </xsl:template>

</xsl:stylesheet>