package net.noorg.test;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Thomas Lehmann
 *
 */
public class Business {
	
	public void doSomething() throws JSONException {
		System.out.println(":))");
		
		JSONObject json = new JSONObject("{\"smile\": \":)))\"}");
		
		System.out.println(json.get("smile"));
	}
	
}
