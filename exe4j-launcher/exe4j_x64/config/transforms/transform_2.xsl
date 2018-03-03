<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="">

  <xsl:output indent="yes"/>

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="splashScreen">
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:if test="@show='true' and (@java6SplashScreen='false' or not(@java6SplashScreen))">
        <xsl:attribute name="windowsNative">true</xsl:attribute>
        <xsl:attribute name="textOverlay">true</xsl:attribute>
      </xsl:if>
      <xsl:apply-templates/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="statusLine | versionLine">
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:attribute name="bold">
        <xsl:value-of select="string(@fontWeight &gt; 500)"/>
      </xsl:attribute>
    </xsl:copy>
  </xsl:template>


</xsl:stylesheet>