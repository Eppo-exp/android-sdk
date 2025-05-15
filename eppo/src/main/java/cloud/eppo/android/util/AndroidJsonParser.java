package cloud.eppo.android.util;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import cloud.eppo.Utils;
import cloud.eppo.api.EppoValue;
import cloud.eppo.exception.JsonParsingException;
import cloud.eppo.ufc.dto.BanditParametersResponse;
import cloud.eppo.ufc.dto.FlagConfigResponse;

public class AndroidJsonParser implements Utils.JsonDecoder {


  @Override
  public FlagConfigResponse parseFlagConfigResponse(byte[] jsonString) throws JsonParsingException {
    return null;
  }

  @Override
  public BanditParametersResponse parseBanditParametersResponse(byte[] jsonString) throws JsonParsingException {
    return null;
  }

  @Override
  public boolean isValidJson(String json) {
    try {
      new JSONObject(json);
      return true;
    } catch (JSONException ex) {
      try {
        new JSONArray(json);
        return true;
      } catch (JSONException ex1) {
        return false;
      }
    }
  }

  @Override
  public String serializeAttributesToJSONString(Map<String, EppoValue> map, boolean omitNulls) {
    try {
      JSONObject jsonObject = new JSONObject();
      
      for (Map.Entry<String, EppoValue> entry : map.entrySet()) {
        EppoValue value = entry.getValue();
        String key = entry.getKey();
        
        // Skip null values if omitNulls is true
        if (omitNulls && (value == null || value.isNull())) {
          continue;
        }
        
        if (value == null || value.isNull()) {
          jsonObject.put(key, JSONObject.NULL);
        } else if (value.isBoolean()) {
          jsonObject.put(key, value.booleanValue());
        } else if (value.isNumeric()) {
          jsonObject.put(key, value.doubleValue());
        } else if (value.isString()) {
          jsonObject.put(key, value.stringValue());
        } else if (value.isStringArray()) {
          JSONArray jsonArray = new JSONArray();
          for (String str : value.stringArrayValue()) {
            jsonArray.put(str);
          }
          jsonObject.put(key, jsonArray);
        }
      }
      
      return jsonObject.toString();
    } catch (JSONException e) {
      // In case of serialization error, return empty JSON object
      return "{}";
    }
  }
}
