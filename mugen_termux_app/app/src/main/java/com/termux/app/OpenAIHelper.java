package com.termux.app;

import android.os.Handler;
import android.os.Looper;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

public class OpenAIHelper {
    // La clé est lue dynamiquement depuis le fichier .secret/.cle_openai
    private static String getOpenAIKey() {
        java.io.File file = new java.io.File("/workspaces/mugen_termux/.secret/.cle_openai");
        if (!file.exists()) return null;
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
            String key = reader.readLine();
            reader.close();
            return key != null ? key.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    public interface OpenAIListener {
        void onSuccess(String response);
        void onError(String error);
    }

    public static void ask(String question, OpenAIListener listener) {
        OkHttpClient client = new OkHttpClient();
        String OPENAI_API_KEY = getOpenAIKey();
        if (OPENAI_API_KEY == null || OPENAI_API_KEY.isEmpty()) {
            listener.onError("Clé OpenAI manquante. Placez-la dans .secret/.cle_openai");
            return;
        }
        JSONObject json = new JSONObject();
        try {
            json.put("model", "gpt-3.5-turbo");
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", question);
            messages.put(userMsg);
            json.put("messages", messages);
        } catch (Exception e) {
            listener.onError("Erreur JSON: " + e.getMessage());
            return;
        }
        RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> listener.onError("Erreur réseau: " + e.getMessage()));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onError("Erreur API: " + response.message()));
                    return;
                }
                String resp = response.body().string();
                try {
                    JSONObject obj = new JSONObject(resp);
                    JSONArray choices = obj.getJSONArray("choices");
                    String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
                    new Handler(Looper.getMainLooper()).post(() -> listener.onSuccess(content.trim()));
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onError("Erreur parsing: " + e.getMessage()));
                }
            }
        });
    }
}
