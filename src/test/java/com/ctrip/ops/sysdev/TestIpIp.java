package com.ctrip.ops.sysdev;

import com.ctrip.ops.sysdev.filters.IpIp;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TestIpIp {
	@Test
	@SuppressWarnings({ "rawtypes", "unchecked", "serial" })
	public void testDateFilter() throws UnsupportedEncodingException {

		URL resource = TestIpIp.class.getResource("/ipip/17monipdb.dat");

		String c = "source: remote_addr\n" +
					"target: ipip \n" +
					"database: " + resource.getPath();


		Yaml yaml = new Yaml();
		Map config = (Map) yaml.load(c);
		Assert.assertNotNull(config);

		IpIp ipip = new IpIp(config);

		Map event = new HashMap();

		// Good
		event.put("remote_addr","12.40.10.15");
		event = ipip.process(event);

		Map result = (Map)event.get(config.get("target"));
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.get("city"));
		Assert.assertNotNull(result.get("state"));
		Assert.assertNotNull(result.get("country"));


		// Not Good
		event = new HashMap();
		event.put("remote_addr", "");
		event = ipip.process(event);
		Assert.assertNull(event.get(config.get("target")));

	}
}
