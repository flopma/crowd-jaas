JAAS LoginModule for Crowd

---

This repository contains a JAAS LoginModule for Rundeck v3.0.x which integrates with Atlassian Crowd.

<ol>
<li>Clone the source

<pre><code>$ git clone -v --progress https://github.com/flopma/crowd-jaas.git
$ cd crowd-jaas
$ git checkout rundeck-3.0</code></pre>

</li>
<li>Build the jar from the source
<pre><code>cd jetty/jaas-jetty-crowd
mvn package
</code></pre>
</li>
<li>Copy the JAR target/jaas-jetty-crowd-&lt;version&gt;-jar-with-dependencies-packed.jar in folder server/lib</li>
<li>Setup <a href="https://rundeck.org/docs/administration/security/authenticating-users.html#jetty-and-jaas-authentication">JAAS LoginModule</a> to contain the following settings
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
<li>Setup Crowd and rundeck to allow authorization to happen (Crowd groups / <a href="https://rundeck.org/docs/administration/security/access-control-policy.html">Rundeck ACL Policies</a>)</li>
<li>Make sure to launch Rundeck with the JAAS login feature enabled (rundeck.jaaslogin=true). See https://rundeck.org/docs/administration/install/launcher.html#system-properties</li>
</ol>
