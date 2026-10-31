package com.tenemsa.platform.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under "ai.*" (see application.properties). Everything that costs money or can hang
 * is limited here: timeout, input size, per-user and per-company quotas.
 *
 * provider = "none"          AI is switched off; AI endpoints answer 503 with a clear message.
 * provider = "google-genai"  Gemini through Spring AI. The API key lives only in the backend environment.
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String provider = "none";
    private String model = "gemini-2.5-flash";
    private double temperature = 0.2;
    private int timeoutSeconds = 45;
    private int maxInputChars = 20000;
    /** Extra attempts when the model returns output that is not valid JSON / fails validation. */
    private int maxOutputRepairs = 1;
    private int perUserPerHour = 20;
    private int perCompanyPerDay = 300;
    /** Optional price list (US dollars per million tokens) used only for the cost ESTIMATE on the usage page. 0 = hide it. */
    private double priceInputPerMillionUsd = 0;
    private double priceOutputPerMillionUsd = 0;

    public boolean isEnabled() { return provider != null && !provider.isBlank() && !"none".equalsIgnoreCase(provider); }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxInputChars() { return maxInputChars; }
    public void setMaxInputChars(int maxInputChars) { this.maxInputChars = maxInputChars; }
    public int getMaxOutputRepairs() { return maxOutputRepairs; }
    public void setMaxOutputRepairs(int maxOutputRepairs) { this.maxOutputRepairs = maxOutputRepairs; }
    public double getPriceInputPerMillionUsd() { return priceInputPerMillionUsd; }
    public void setPriceInputPerMillionUsd(double v) { this.priceInputPerMillionUsd = v; }
    public double getPriceOutputPerMillionUsd() { return priceOutputPerMillionUsd; }
    public void setPriceOutputPerMillionUsd(double v) { this.priceOutputPerMillionUsd = v; }
    public int getPerUserPerHour() { return perUserPerHour; }
    public void setPerUserPerHour(int perUserPerHour) { this.perUserPerHour = perUserPerHour; }
    public int getPerCompanyPerDay() { return perCompanyPerDay; }
    public void setPerCompanyPerDay(int perCompanyPerDay) { this.perCompanyPerDay = perCompanyPerDay; }
}
