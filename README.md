JAAS LoginModule for Crowd

---

This repository contains a JAAS LoginModule for Jetty which integrates with Atlassian Crowd. The need is to run [Rundeck](http://rundeck.org) against Crowd. But it should be usable for any other application compatible with JAAS/Jetty

<ol>
<li>Clone the source

<pre><code>$ git clone -v --progress https://github.com/flopma/crowd-jaas.git /your/working/folder</code></pre>

</li>
<li>Build the jar from the source
<pre><code>cd jetty/jaas-jetty-crowd
mvn package
</code></pre>
</li>
<li>Uncompress the zip target/jaas-jetty-crowd-<version>-jar-with-dependencies-packed.zip</li>
<li>Setup JAAS LoginModule to contain the following settings
<pre><code>be.greenhand.jaas.jetty.CrowdLoginModule sufficient
	applicationName="your jetty app"
	applicationPassword="a password"
	crowdServerUrl="https://example.com/crowd/"
	httpMaxConnections="20"
	httpTimeout="5000";
</code></pre>

If your web app needs to connect to Crowd through a proxy, use the following settings

<pre><code>be.greenhand.jaas.jetty.CrowdLoginModule sufficient
	applicationName="your jetty app"
	applicationPassword="a password"
	crowdServerUrl="https://example.com/crowd/"
	httpMaxConnections="20"
	httpTimeout="5000"
	httpProxyHost="yourproxyhostname"
	httpProxyPort="proxyportnumber"
	httpProxyUsername="proxyusername - if authentication required"
	httpProxyPassword="proxypassword - if authentication required";
</code></pre>
</li>
<li>Setup Crowd to accept requests from this application</li>
<li>Setup Crowd and your appplication to allow authorization to happen (Crowd groups / Servlet Security Roles) - this is application specific</li>
</ol>
