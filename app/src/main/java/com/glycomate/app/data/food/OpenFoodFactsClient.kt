package com.glycomate.app.data.food

import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private const val TAG = "OpenFoodFacts"

// ── OFF API models ────────────────────────────────────────────────────────────

data class OffProductResponse(
    @SerializedName("status")        val status:  Int         = 0,  // 1=found, 0=not found
    @SerializedName("status_verbose") val verbose: String     = "",
    @SerializedName("product")       val product: OffProduct? = null
)

data class OffProduct(
    @SerializedName("product_name")    val name:       String    = "",
    @SerializedName("product_name_el") val nameEl:     String    = "",  // Greek name
    @SerializedName("brands")          val brands:     String    = "",
    @SerializedName("quantity")        val quantity:   String    = "",
    @SerializedName("serving_size")    val servingSize:String    = "",
    @SerializedName("nutriments")      val nutriments: OffNutriments? = null,
    @SerializedName("image_url")       val imageUrl:   String    = "",
    @SerializedName("categories")      val categories: String    = ""
)

data class OffNutriments(
    // per 100g values
    @SerializedName("carbohydrates_100g")         val carbs100g:      Double? = null,
    @SerializedName("sugars_100g")                val sugars100g:     Double? = null,
    @SerializedName("energy-kcal_100g")           val calories100g:   Double? = null,
    @SerializedName("fat_100g")                   val fat100g:        Double? = null,
    @SerializedName("proteins_100g")              val protein100g:    Double? = null,
    @SerializedName("fiber_100g")                 val fiber100g:      Double? = null,
    // per serving values (optional)
    @SerializedName("carbohydrates_serving")      val carbsServing:   Double? = null,
    @SerializedName("energy-kcal_serving")        val caloriesServing:Double? = null
)

// ── Search result models ──────────────────────────────────────────────────────

data class OffSearchResponse(
    @SerializedName("count")    val count:    Int            = 0,
    @SerializedName("products") val products: List<OffProduct> = emptyList()
)

// ── Parsed food item ──────────────────────────────────────────────────────────

data class FoodProduct(
    val barcode:       String,
    val name:          String,
    val brand:         String,
    val carbs100g:     Float,    // carbohydrates per 100g
    val calories100g:  Float,
    val sugars100g:    Float,
    val servingSize:   String,   // e.g. "30g", "1 slice"
    val servingCarbs:  Float,    // carbs per serving (0 if unknown)
    val imageUrl:      String,
    val glycemicIndex: String    // estimated: "Χαμηλό" / "Μέτριο" / "Υψηλό"
) {
    /** Calculate carbs for a given weight in grams */
    fun carbsForWeight(weightGrams: Float): Float =
        (carbs100g / 100f * weightGrams)

    /** Display-friendly serving info */
    val servingInfo: String get() = when {
        servingSize.isNotBlank() && servingCarbs > 0f ->
            "Μερίδα: $servingSize = ${servingCarbs.toInt()}g carbs"
        servingSize.isNotBlank() ->
            "Μερίδα: $servingSize"
        else -> "${carbs100g.toInt()}g carbs / 100g"
    }
}

// ── Retrofit interface ────────────────────────────────────────────────────────

interface OffApi {
    @GET("api/v0/product/{barcode}.json")
    suspend fun getByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = OFF_FIELDS
    ): Response<OffProductResponse>

    @GET("cgi/search.pl")
    suspend fun search(
        @Query("search_terms")      query:       String,
        @Query("search_simple")     simple:      Int    = 1,
        @Query("action")            action:      String = "process",
        @Query("json")              json:        Int    = 1,
        @Query("page_size")         pageSize:    Int    = 10,
        @Query("fields")            fields:      String = OFF_FIELDS,
        @Query("lc")                lang:        String = "el,en"
    ): Response<OffSearchResponse>
}

private const val OFF_FIELDS =
    "product_name,product_name_el,brands,quantity,serving_size," +
    "nutriments,image_url,categories"

// ── Result ────────────────────────────────────────────────────────────────────

sealed class FoodResult<out T> {
    data class Success<T>(val data: T)             : FoodResult<T>()
    data class NotFound(val barcode: String)       : FoodResult<Nothing>()
    data class Error(val message: String)          : FoodResult<Nothing>()
}

// ── Client ────────────────────────────────────────────────────────────────────

class OpenFoodFactsClient {

    private val api: OffApi = buildApi()

    /** Look up a product by barcode (EAN-13, EAN-8, UPC, etc.) */
    suspend fun getProduct(barcode: String): FoodResult<FoodProduct> {
        return try {
            Log.d(TAG, "Looking up barcode: $barcode")
            val resp = api.getByBarcode(barcode.trim())
            when {
                !resp.isSuccessful ->
                    FoodResult.Error("HTTP ${resp.code()}")
                resp.body()?.status == 0 ->
                    FoodResult.NotFound(barcode)
                else -> {
                    val product = resp.body()?.product
                        ?: return FoodResult.NotFound(barcode)
                    val fp = product.toFoodProduct(barcode)
                    if (fp.carbs100g <= 0f) {
                        // Product found but no nutritional data
                        Log.w(TAG, "Product found but no carbs data: ${fp.name}")
                    }
                    Log.d(TAG, "Found: ${fp.name} — ${fp.carbs100g}g carbs/100g")
                    FoodResult.Success(fp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getProduct failed", e)
            FoodResult.Error("Σφάλμα δικτύου: ${e.localizedMessage}")
        }
    }

    /** Search by product name */
    suspend fun search(query: String): FoodResult<List<FoodProduct>> {
        return try {
            Log.d(TAG, "Searching: $query")
            val resp = api.search(query)
            if (resp.isSuccessful) {
                val products = resp.body()?.products
                    ?.filter { it.nutriments?.carbs100g != null }
                    ?.map { it.toFoodProduct("") }
                    ?: emptyList()
                FoodResult.Success(products)
            } else {
                FoodResult.Error("HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            FoodResult.Error("Σφάλμα δικτύου: ${e.localizedMessage}")
        }
    }

    private fun buildApi(): OffApi {
        val logging = HttpLoggingInterceptor { Log.d(TAG, it) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            // Identify ourselves per OFF API guidelines
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "GlycoMate-Android/1.0 (diabetes management app)")
                    .build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OffApi::class.java)
    }
}

// ── Extensions ────────────────────────────────────────────────────────────────

private fun OffProduct.toFoodProduct(barcode: String): FoodProduct {
    val n = nutriments
    val carbs100  = n?.carbs100g?.toFloat()  ?: 0f
    val cal100    = n?.calories100g?.toFloat() ?: 0f
    val sugars100 = n?.sugars100g?.toFloat() ?: 0f
    val carbsServ = n?.carbsServing?.toFloat() ?: 0f

    // Use Greek name if available, otherwise English
    val displayName = nameEl.ifBlank { name }.ifBlank { "Άγνωστο προϊόν" }

    return FoodProduct(
        barcode      = barcode,
        name         = displayName,
        brand        = brands.split(",").firstOrNull()?.trim() ?: "",
        carbs100g    = carbs100,
        calories100g = cal100,
        sugars100g   = sugars100,
        servingSize  = servingSize,
        servingCarbs = carbsServ,
        imageUrl     = imageUrl,
        glycemicIndex = estimateGI(carbs100, sugars100, categories)
    )
}

/**
 * Simple GI estimation based on sugar content and category.
 * NOT a medical tool — rough estimate only.
 */
private fun estimateGI(carbs100g: Float, sugars100g: Float, categories: String): String {
    val cat = categories.lowercase()
    return when {
        // Obvious high GI categories
        cat.contains("candies") || cat.contains("sugar") ||
        cat.contains("sodas") || cat.contains("juices")   -> "Υψηλό"

        // Obviously low GI
        cat.contains("vegetables") || cat.contains("legumes") ||
        cat.contains("nuts")                               -> "Χαμηλό"

        // Ratio of sugars to total carbs
        carbs100g > 0f && (sugars100g / carbs100g) > 0.7f -> "Υψηλό"
        carbs100g > 0f && (sugars100g / carbs100g) < 0.25f -> "Χαμηλό"
        else                                               -> "Μέτριο"
    }
}
