JAAS LoginModule for Crowd

---

This repository contains a JAAS LoginModule for Rundeck which integrates with Atlassian Crowd.

<ol>
<li>Clone the source

<pre><code>$ git clone -v --progress https://github.com/flopma/crowd-jaas.git
$ cd crowd-jaas
$ git checkout rundeck-2.7</code></pre>

</li>
<li>Build the jar from the source
<pre><code>cd jetty/jaas-jetty-crowd
mvn package
</code></pre>
</li>
<li>Uncompress the zip target/jaas-jetty-crowd-<version>-jar-with-dependencies-packed.zip in folder server/lib (when using the RPM package, uncompress it in /var/lib/rundeck/exp/webapp/WEB-INF/lib)</li>
<li>Setup <a href="http://rundeck.org/docs/administration/authenticating-users.html">JAAS LoginModule</a> to contain the following settings
<pre><code>be.greenhand.jaas.jetty.CrowdLoginModule sufficient
	applicationName="rundeck"
	applicationPassword="a password"
	crowdServerUrl="https://example.com/crowd/"
	httpMaxConnections="20"
	httpTimeout="5000";
</code></pre>

If Rundeck needs to connect to Crowd through a proxy, use the following settings

<pre><code>be.greenhand.jaas.jetty.CrowdLoginModule sufficient
	applicationName="rundeck"
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
<li>Setup Crowd to accept requests from rundeck</li>
<li>Setup Crowd and rundeck to allow authorization to happen (Crowd groups / <a href="http://rundeck.org/docs/administration/access-control-policy.html">Rundeck ACL Policies</a>)</li>
</ol>
