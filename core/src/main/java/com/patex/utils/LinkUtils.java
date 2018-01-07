package com.patex.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;

/**
 * Created by Alexey on 07.05.2017.
 */
public class LinkUtils {
    public static final String SLASH = "/";
    private static Logger log = LoggerFactory.getLogger(LinkUtils.class);

    public static String makeURL(Object... parts) {
        return Arrays.stream(parts).map(String::valueOf).
                map(s -> s.startsWith(SLASH) ? s.substring(1) : s).
                map(s -> s.endsWith(SLASH) ? s.substring(0, s.length() - 1) : s)
                .reduce("", (s, s2) -> s + SLASH + s2);
    }

    public static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.warn("unsuported encoding error message {}", e.getMessage());
            return text;
        }
    }
}
