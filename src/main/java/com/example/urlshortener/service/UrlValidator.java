package com.example.urlshortener.service;

import com.example.urlshortener.exception.InvalidUrlException;
import org.springframework.stereotype.Component;
import java.net.*;

@Component
public class UrlValidator {
    private static final int MAX_URL_LENGTH = 2048;
    public void validate(String url) {
        if (url == null || url.isBlank()) throw new InvalidUrlException("URL must not be blank");
        if (url.length() > MAX_URL_LENGTH) throw new InvalidUrlException("URL must not exceed 2048 characters");
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) throw new InvalidUrlException("URL must use http or https");
            String host = uri.getHost();
            if (host == null) throw new InvalidUrlException("URL must contain a valid host");
            if (isUnsafeHost(host)) throw new InvalidUrlException("URL host must not resolve to a local or private address");
        } catch (URISyntaxException e) { throw new InvalidUrlException("URL is malformed"); }
    }
    private boolean isUnsafeHost(String host) {
        String normalized=host.toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("localhost") || normalized.endsWith(".localhost") || normalized.equals("0.0.0.0") || normalized.equals("::1")) return true;
        try { for (InetAddress address : InetAddress.getAllByName(host)) if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()) return true; } catch (UnknownHostException ignored) { }
        return false;
    }
}
