package com.example.unicontrol.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Hier definieren wir alle Endpunkte von Immich.
 * Retrofit kümmert sich um den Rest (Verbindungsaufbau, Threads, JSON).
 */
public interface ImmichApi {

    @GET("api/albums")
    Call<JsonElement> getAlbums(@Header("x-api-key") String apiKey);

    @GET("api/albums/{id}")
    Call<JsonElement> getAlbumDetails(@Header("x-api-key") String apiKey, @Path("id") String albumId);

    @POST("api/search/metadata")
    Call<JsonElement> searchMetadata(@Header("x-api-key") String apiKey, @Body JsonObject body);

    @POST("api/search/smart")
    Call<JsonElement> searchSmart(@Header("x-api-key") String apiKey, @Body JsonObject body);

    @GET("api/memories")
    Call<JsonElement> getMemories(@Header("x-api-key") String apiKey, @Query("for") String date);

    @GET("api/people")
    Call<JsonElement> getPeople(@Header("x-api-key") String apiKey);

    @GET("api/search/cities")
    Call<JsonElement> getCities(@Header("x-api-key") String apiKey);

    @PUT("api/assets/{id}")
    Call<Void> updateAsset(@Header("x-api-key") String apiKey, @Path("id") String assetId, @Body JsonObject body);

    @DELETE("api/asset")
    Call<Void> deleteAssets(@Header("x-api-key") String apiKey, @Body JsonObject body);

    // Bemerkung: Wir nutzen hier erstmal "JsonElement" als Rückgabe,
    // damit wir die neue Logik perfekt an deine bereits bestehenden
    // JSON-Parsing-Methoden im FotosFragment andocken können!
}