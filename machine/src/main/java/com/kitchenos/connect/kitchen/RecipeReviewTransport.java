package com.kitchenos.connect.kitchen;
import android.os.CancellationSignal;import org.json.JSONObject;
public interface RecipeReviewTransport {JSONObject send(CloudSession session,JSONObject input,CancellationSignal cancellation)throws Exception;}
