package com.kitchenos.connect.kitchen;
import android.os.CancellationSignal;import org.json.JSONObject;
/** Cancellable boundary between an owned preparation job and its online request. */
public interface RecipePreparationTransport {JSONObject prepare(CloudSession session,JSONObject input,CancellationSignal cancellation)throws Exception;}
