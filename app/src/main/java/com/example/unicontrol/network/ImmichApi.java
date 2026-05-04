package com.example.unicontrol.network;

import com.example.unicontrol.models.requests.ImmichRequests;
import com.google.gson.JsonElement;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

/**
 * Hier definieren wir alle Endpunkte von Immich.
 * Retrofit kümmert sich um den Rest (Verbindungsaufbau, Threads, JSON).
 */
public interface ImmichApi {

    @GET("api/albums")
    Call<JsonElement> getAlbums(@Header("x-api-key") String apiKey);

    @GET("api/albums/{id}")
    Call<JsonElement> getAlbumDetails(@Header("x-api-key") String apiKey, @Path("id") String albumId);

    @GET("api/album/{id}") // Fallback
    Call<JsonElement> getAlbumDetailsLegacy(@Header("x-api-key") String apiKey, @Path("id") String albumId);

    @POST("api/search/metadata")
    Call<JsonElement> searchMetadata(@Header("x-api-key") String apiKey, @Body ImmichRequests.SearchMetadata body);

    @POST("api/search/smart")
    Call<JsonElement> searchSmart(@Header("x-api-key") String apiKey, @Body ImmichRequests.SmartSearch body);

    @GET("api/memories")
    Call<JsonElement> getMemories(@Header("x-api-key") String apiKey, @Query("for") String date);

    @GET("api/people")
    Call<JsonElement> getPeople(@Header("x-api-key") String apiKey);

    @GET("api/person") // Fallback
    Call<JsonElement> getPeopleLegacy(@Header("x-api-key") String apiKey);

    @PUT("api/people/{id}")
    Call<JsonElement> updatePerson(@Header("x-api-key") String apiKey, @Path("id") String id, @Body ImmichRequests.UpdatePerson body);

    @GET("api/search/cities")
    Call<JsonElement> getCities(@Header("x-api-key") String apiKey);

    @PUT("api/assets/{id}")
    Call<JsonElement> updateAsset(@Header("x-api-key") String apiKey, @Path("id") String assetId, @Body ImmichRequests.UpdateAsset body);

    @HTTP(method = "DELETE", path = "api/asset", hasBody = true)
    Call<Void> deleteAssets(@Header("x-api-key") String apiKey, @Body ImmichRequests.Ids body);

    @HTTP(method = "DELETE", path = "api/assets", hasBody = true) // Fallback
    Call<Void> deleteAssetsLegacy(@Header("x-api-key") String apiKey, @Body ImmichRequests.Ids body);

    @POST("api/asset/bulk-upload-check")
    Call<JsonElement> bulkUploadCheck(@Header("x-api-key") String apiKey, @Body ImmichRequests.BulkUploadCheck body);

    @POST("api/assets/bulk-upload-check") // Fallback
    Call<JsonElement> bulkUploadCheckLegacy(@Header("x-api-key") String apiKey, @Body ImmichRequests.BulkUploadCheck body);

    @GET("api/timeline/buckets")
    Call<JsonElement> getTimeBuckets(@Header("x-api-key") String apiKey, @Query("size") String size);

    @GET("api/asset/time-buckets") // Fallback
    Call<JsonElement> getTimeBucketsLegacy(@Header("x-api-key") String apiKey);

    @PUT("api/albums/{id}/assets")
    Call<JsonElement> addAssetsToAlbum(@Header("x-api-key") String apiKey, @Path("id") String id, @Body ImmichRequests.AssetIds body);

    @HTTP(method = "DELETE", path = "api/albums/{id}/assets", hasBody = true)
    Call<JsonElement> removeAssetsFromAlbum(@Header("x-api-key") String apiKey, @Path("id") String id, @Body ImmichRequests.AssetIds body);

    @Multipart
    @POST("api/assets")
    Call<JsonElement> uploadAsset(
            @Header("x-api-key") String apiKey,
            @Part("deviceAssetId") RequestBody deviceAssetId,
            @Part("deviceId") RequestBody deviceId,
            @Part("fileCreatedAt") RequestBody fileCreatedAt,
            @Part("fileModifiedAt") RequestBody fileModifiedAt,
            @Part("isFavorite") RequestBody isFavorite,
            @Part MultipartBody.Part assetData
    );

    @GET("api/assets/{id}/original")
    @Streaming
    Call<ResponseBody> downloadAssetOriginal(@Header("x-api-key") String apiKey, @Path("id") String id);

    @GET("api/assets/{id}")
    Call<JsonElement> getAssetDetails(@Header("x-api-key") String apiKey, @Path("id") String id);
}