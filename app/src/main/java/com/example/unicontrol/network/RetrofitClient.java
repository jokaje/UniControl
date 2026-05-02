package com.example.unicontrol.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static Retrofit retrofit = null;
    private static String currentBaseUrl = "";

    public static ImmichApi getImmichApi(String baseUrl) {
        // Retrofit verlangt, dass die Base-URL zwingend mit einem "/" endet
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        // Wenn wir noch keinen Client haben oder sich deine URL geändert hat
        // (z.B. Wechsel von WLAN auf Mobilfunk), bauen wir den Client neu.
        if (retrofit == null || !currentBaseUrl.equals(baseUrl)) {
            currentBaseUrl = baseUrl;

            // Optional: Großzügige Timeouts, falls die Cloud mal langsam ist
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit.create(ImmichApi.class);
    }
}