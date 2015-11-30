package com.gnut3ll4.reznet;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.prefs.Preferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

public class Main {

	private final static int DETAILED_BANDWITH_INDEX = 0;
	private final static int QUOTA_INDEX = 1;
	private final static int PORT = 0;
	private final static int UPLOAD = 2;
	private final static int DOWNLOAD = 3;
	private final static String CONTENT = "content";

	private static JSONObject query;

	final static String SENT_50 = "SENT_50";
	final static String SENT_75 = "SENT_75";
	final static String SENT_90 = "SENT_90";

	private static String phase;
	private static String app;

	private static String[] addresses;
	private static String SMTPUsername;
	private static String SMTPPassword;
	private static boolean SMTPAuth;
	private static boolean SMTPStarttlsEnable;
	private static String SMTPHost;
	private static int SMTPPort;

	public static void main(String[] args) {

		Preferences prefs = Preferences
				.userNodeForPackage(com.gnut3ll4.reznet.Main.class);
		loadConfigProperties();

		Calendar cal = Calendar.getInstance();
		int currentMonth = Integer.valueOf(new SimpleDateFormat("MM")
				.format(cal.getTime()));

		int currentDay = Integer.valueOf(new SimpleDateFormat("dd").format(cal
				.getTime()));

		if (currentDay < 5) {
			prefs.putBoolean(SENT_50, false);
			prefs.putBoolean(SENT_75, false);
			prefs.putBoolean(SENT_90, false);
		}

		try {

			OkHttpClient client = new OkHttpClient();

			Request request = new Request.Builder()
					.url("http://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20html%20where%20url%3D%22http%3A%2F%2Fets-res"
							+ phase
							+ "-"
							+ app
							+ "%3Aets"
							+ app
							+ "%40www2.cooptel.qc.ca%2Fservices%2Ftemps%2F%3Fmois%3D"
							+ currentMonth
							+ "%26cmd%3DVisualiser%22%20and%20xpath%3D'%2F%2Ftable%5B%40border%3D%221%22%5D'&format=json&diagnostics=true&callback=")
					.get().build();

			Response response = client.newCall(request).execute();

			int code = response.code();
			if (code == 200) {
				try {

					String json = response.body().string();
					JSONObject obj = new JSONObject(json);
					query = (JSONObject) obj.get("query");

				} catch (JSONException e) {
					e.printStackTrace();
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				} catch (UnsupportedOperationException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (query.getJSONObject("results") != null) {
				JSONObject results = (JSONObject) query.get("results");
				JSONArray arrayTable = results.getJSONArray("table");
				JSONObject tableauElem = arrayTable.getJSONObject(
						DETAILED_BANDWITH_INDEX).getJSONObject("tbody");
				JSONObject quota = arrayTable.getJSONObject(QUOTA_INDEX)
						.getJSONObject("tbody");
				JSONArray arrayElem = tableauElem.getJSONArray("tr");
				HashMap<String, Double> map = getBandwithUserFromPort(arrayElem);
				int size = map.size();
				double[] values = new double[size];
				String[] rooms = new String[size];
				Iterator<String> iter = map.keySet().iterator();
				int i = 0;

				while (iter.hasNext()) {
					String entry = iter.next();
					if (!entry.equals("total")) {
						double value = map.get(entry);
						values[i] = Math.round((value / 1024) * 100) / 100.0;
						String[] stringArray = entry.split("-");

						if (stringArray.length > 1) {
							rooms[i] = "■ " + stringArray[1].toString() + " "
									+ values[i] + " Go";

						} else {
							int j = i + 1;
							rooms[i] = "■ Chambre" + j + " " + values[i]
									+ " Go";
						}
						i++;
					}
				}

				JSONArray quotaJson = quota.getJSONArray("tr");
				JSONObject objectQuota = (JSONObject) quotaJson.get(1);
				JSONArray arrayQuota = objectQuota.getJSONArray("td");
				double quotaValue = ((JSONObject) arrayQuota.get(1))
						.getDouble(CONTENT);
				quotaValue = Math.round(quotaValue / 1024 * 100) / 100.0;
				double total = map.get("total");
				total = Math.round(total / 1024 * 100) / 100.0;
				double rest = Math.round((quotaValue - total) * 100) / 100.0;
				values[size - 1] = rest;
				rooms[size - 1] = "■ Restant " + rest + " Go";

				double pourcentageRestant = round(
						(1 - total / quotaValue) * 100.0, 2);

				String message = "Il reste " + rest + " Go, soit "
						+ pourcentageRestant + "% du total !\n";
				message += total + " Go / " + quotaValue + " Go\n";
				message += "\nDétails des chambres :\n";
				for (String room : rooms) {
					message += room + "\n";
				}

				if (pourcentageRestant <= 50 && pourcentageRestant > 25
						&& !prefs.getBoolean(SENT_50, false)) {
					EmailUtil.sendEmail(SMTPUsername, SMTPPassword, SMTPAuth,
							SMTPStarttlsEnable, SMTPHost, SMTPPort, addresses,
							pourcentageRestant
									+ "% de l'internet mensuel restant",
							message);
					prefs.putBoolean(SENT_50, true);
				}

				if (pourcentageRestant <= 25 && pourcentageRestant > 10
						&& !prefs.getBoolean(SENT_75, false)) {
					EmailUtil.sendEmail(SMTPUsername, SMTPPassword, SMTPAuth,
							SMTPStarttlsEnable, SMTPHost, SMTPPort, addresses,
							pourcentageRestant
									+ "% de l'internet mensuel restant",
							message);
					prefs.putBoolean(SENT_75, true);
				}

				if (pourcentageRestant <= 10
						&& !prefs.getBoolean(SENT_90, false)) {
					EmailUtil.sendEmail(SMTPUsername, SMTPPassword, SMTPAuth,
							SMTPStarttlsEnable, SMTPHost, SMTPPort, addresses,
							"Alerte générale : " + pourcentageRestant
									+ "% de l'internet mensuel restant",
							message);
					prefs.putBoolean(SENT_90, true);
				}

				System.out.println(message);

			} else {
				System.out.println("error");
			}

		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

	}

	public static double round(double value, int places) {
		if (places < 0)
			throw new IllegalArgumentException();

		BigDecimal bd = new BigDecimal(value);
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}

	private static HashMap<String, Double> getBandwithUserFromPort(
			JSONArray array) {
		HashMap<String, Double> map = new HashMap<>();
		try {
			for (int i = 1; i < array.length(); i++) {
				JSONObject obj = (JSONObject) array.get(i);
				JSONArray elem = obj.getJSONArray("td");
				final boolean IS_NOT_LAST_ELEMENT = i < array.length() - 2;
				final boolean IS_LAST_ELEMENT = i == array.length() - 1;

				if (IS_NOT_LAST_ELEMENT) {
					String portElem = elem.getString(PORT);
					if (containtPort(portElem, map) != null)
						portElem = containtPort(portElem, map);

					JSONObject upload = elem.getJSONObject(UPLOAD);
					JSONObject downLoad = elem.getJSONObject(DOWNLOAD);
					double downUpLoad = upload.getDouble(CONTENT)
							+ downLoad.getDouble(CONTENT);
					if (map.containsKey(portElem)) {
						double downUpLoadValue = map.get(portElem);
						downUpLoad += downUpLoadValue;
					}
					map.put(portElem, downUpLoad);
				} else if (IS_LAST_ELEMENT) {
					JSONObject totalObject = (JSONObject) elem.get(1);
					double total = totalObject.getDouble(CONTENT);
					map.put("total", total);
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return map;
	}

	private static String containtPort(String port, HashMap<String, Double> map) {
		Iterator<String> iter = map.keySet().iterator();
		while (iter.hasNext()) {
			String entry = iter.next();
			if (entry.contains(port)) {
				return entry;
			}
		}
		return null;
	}

	public static void loadConfigProperties() {
		Preferences prefs = Preferences
				.userNodeForPackage(com.gnut3ll4.reznet.Main.class);
		GetPropertyValues properties = new GetPropertyValues();

		try {
			phase = properties.getPropValues("phase");
			app = properties.getPropValues("app");
			addresses = properties.getPropValues("addresses").split(",");

			SMTPUsername = properties.getPropValues("SMTPUsername");
			SMTPPassword = properties.getPropValues("SMTPPassword");
			SMTPAuth = Boolean.valueOf(properties.getPropValues("SMTPAuth"));
			SMTPStarttlsEnable = Boolean.valueOf(properties
					.getPropValues("SMTPStarttlsEnable"));
			SMTPHost = properties.getPropValues("SMTPHost");
			SMTPPort = Integer.valueOf(properties.getPropValues("SMTPPort"));

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
