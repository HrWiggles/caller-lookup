/*
 * From project ReMeMore-Moi - http://code.google.com/p/rememore-moi
 * Code license: Apache License 2.0
 */
package com.googlecode.CallerLookup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebParser {
    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; U; CPU iPhone OS 3_0 like Mac OS X; en-us) AppleWebKit/528.18 (KHTML, like Gecko) Version/4.0 Mobile/7A341 Safari/528.16";

    private String lookupParam = null;
    private String lookupMatches = null;

    public WebParser(String lookup, String regExp, String param) {
        lookupParam = param;
        if (lookup.length() > 0) {
            String url = lookup.contains("%s") ? String.format(lookup, lookupParam) : lookup;
            String doc = doRequestToString(url);
            if (regExp.length() > 0) {
                Pattern pattern = Pattern.compile(regExp);
                Matcher m = pattern.matcher(doc);
                while (m.find()) {
                    if (lookupMatches == null) lookupMatches = m.group(1).trim();
                    else lookupMatches += "\n" + m.group(1).trim();
                }
            } else {
                lookupMatches = doc;
            }
        }
    }

    public String getMatches() {
        return lookupMatches;
    }

    public String getParam(){
        return lookupParam;
    }

    protected String doRequestToString(String url) {
        StringBuffer ret = new StringBuffer();
        try {
            URL uri = new URL(url);
            URLConnection urlc = uri.openConnection();
            urlc.setRequestProperty("User-Agent", USER_AGENT);
            InputStream is = urlc.getInputStream();

            String tmp;
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            while ((tmp = br.readLine()) != null) {
                ret.append(tmp);
            }
        } catch (MalformedURLException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        return ret.toString();
    }
}

