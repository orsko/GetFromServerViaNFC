package com.example.getfromservervianfc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.text.Html;
import android.text.InputFilter.LengthFilter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	TextView textViewStatus;
	// A változó amibe az ID-t olvassuk
	String read;

	// flag, hogy ne fusson két hálózatos szál egyszerre
	private static boolean running = false;
	private static Object syncObject = new Object();
	// Handler, ami majd módosítani fogja a UI-t
	Handler handler;

	private void RefreshUI(String s) {
		textViewStatus.append(s);
	}

	// NFC-hez kell
	Switch enableWrite;
	Button enableRead;
	EditText StringToWrite;
	IntentFilter[] mWriteTagFilters;
	NfcAdapter mNfcAdapter;
	private PendingIntent mNfcPendingIntent;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		textViewStatus = (TextView) findViewById(R.id.textView1);
		textViewStatus.setText("Oncreate");
		// Handler, ami majd módosítani fogja a UI-t
		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				if (msg.what == 0) {
					Bundle b = msg.getData();
					// Kiírja a kapott szöveget
					String tmp=b.getString("text");
					textViewStatus.setText(tmp);				
				} else if (msg.what == 100) {
					textViewStatus.setText("NEM TUD KAPCSOLÓDNI A SZERVERHEZ");
				}
				super.handleMessage(msg);
			}
		};

		// NFC rész

		

		enableWrite = (Switch) findViewById(R.id.switch1);
		StringToWrite = (EditText) findViewById(R.id.editText1);
		enableWrite.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
				if (arg1) {
					enableWrite();
				} else {
					disableWrite();
				}

			}
		});
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

	}

	class readFromServerAsync extends AsyncTask<URL, Integer, Long>{

		@Override
		protected Long doInBackground(URL... params) {
			HttpClient httpclient = new DefaultHttpClient();
			String URL = "http://nfconlab.appspot.com/rest/q/";
			//Meghívja a kiolvasott String paraméterrel az URL-t
			HttpGet httpGet = new HttpGet(URL + read);
			HttpResponse response;
			HttpEntity entity;
			InputStream instream;
			try {
				response = httpclient.execute(httpGet);
				entity = response.getEntity();
				instream = entity.getContent();
				processResponseAsync(instream, read);
				
			} catch (ClientProtocolException e) {
				// Hiba van, küld egy üzenetet, ami a UI-ra kiírja,
				// hogy hiba
				e.printStackTrace();
			} catch (IOException e) {
				// Hiba van, küld egy üzenetet, ami a UI-ra kiírja,
				// hogy hiba
				e.printStackTrace();
			}
			return null;
		}
		
	}
	// Választ feldolgozó függvény
	private void processResponseAsync(final InputStream aIS, final String s) {

		BufferedReader reader = new BufferedReader(new InputStreamReader(aIS));
		StringBuilder sb = new StringBuilder();
		String line = null;
		String tmp = new String();
		try {
			// kiolvassa sorrol sorra
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
			// Beállítja az üzenetet a handler számára
			tmp=sb.toString();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (aIS != null) {
				try {
					aIS.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			textViewStatus.setText(tmp);
		}

	}
	
	// Olvasás a szervertõl
	private void readFromServer(final String s) {
		// Külön szálban, hogy ne blokkolódjon
		new Thread() {
			public void run() {
				synchronized (syncObject) {
					if (!running) {
						running = true;
						HttpClient httpclient = new DefaultHttpClient();
						String URL = "http://nfconlab.appspot.com/rest/q/";
						HttpGet httpGet = new HttpGet(URL + s);
						HttpResponse response;
						HttpEntity entity;
						InputStream instream;
						try {
							response = httpclient.execute(httpGet);
							entity = response.getEntity();
							instream = entity.getContent();
							processResponse(instream, s);

						} catch (ClientProtocolException e) {
							// Hiba van, küld egy üzenetet, ami a UI-ra kiírja,
							// hogy hiba
							Message msg = handler.obtainMessage();
							msg.what = 100;
							// elküldi az üzenetet, majd a handler módosítja a
							// UI-t
							handler.sendMessage(msg);
							e.printStackTrace();
						} catch (IOException e) {
							// Hiba van, küld egy üzenetet, ami a UI-ra kiírja,
							// hogy hiba
							Message msg = handler.obtainMessage();
							msg.what = 100;
							// elküldi az üzenetet, majd a handler módosítja a
							// UI-t
							handler.sendMessage(msg);
							e.printStackTrace();
						}
						running = false;
					}
				}
			}
		}.start();
	}

	// Választ feldolgozó függvény
	private void processResponse(final InputStream aIS, final String s) {

		BufferedReader reader = new BufferedReader(new InputStreamReader(aIS));
		StringBuilder sb = new StringBuilder();
		String line = null;
		try {
			// kiolvassa sorrol sorra
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
			// Beállítja az üzenetet a handler számára
			Message msg = handler.obtainMessage();
			msg.what = 0;
			Bundle b = new Bundle();
			b.putString("text", new String(sb.toString()));
			msg.setData(b);
			// elküldi az üzenetet, majd a handler módosítja a UI-t
			handler.sendMessage(msg);
			String ssss = new String(sb.toString());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (aIS != null) {
				try {
					aIS.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	protected void enableWrite() {
		IntentFilter tagDetected = new IntentFilter(
				NfcAdapter.ACTION_TAG_DISCOVERED);
		mWriteTagFilters = new IntentFilter[] { tagDetected };
		mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent,
				mWriteTagFilters, null);
	}

	protected void disableWrite() {
		mNfcAdapter.disableForegroundDispatch(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		textViewStatus.setText("OnResume");
		Intent intent = getIntent();
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
			Parcelable[] rawMsgs = intent
					.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			if (rawMsgs != null) {
				NdefMessage[] msgs = new NdefMessage[rawMsgs.length];
				for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
				}
				String message;
				for (NdefMessage tmpMsg : msgs) {
					for (NdefRecord tmpRecord : tmpMsg.getRecords()) {
						// tv.append("\n" + new String(tmpRecord.getPayload()));
						message = new String(tmpRecord.getPayload());
						String tmp = message.substring(1);
						readFromServer(tmp);
						//AsyncTask<URL, Integer, Long> rfsa = new readFromServerAsync().execute();
					}
				}
			}

		}

	}

	@Override
	protected void onNewIntent(Intent intent) {
		// Tag writing mode
		if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
			Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			NdefRecord record1 = createTextRecord(StringToWrite.getText()
					.toString());

			NdefMessage msg = new NdefMessage(new NdefRecord[] { record1 });

			if (writeTag(msg, detectedTag)) {
				Toast.makeText(this, "Success write operation!",
						Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(this, "Failed to write!", Toast.LENGTH_LONG)
						.show();
			}
		}
	}

	public NdefRecord createTextRecord(String payload) {
		byte[] textBytes = payload.getBytes();
		byte[] data = new byte[1 + textBytes.length];
		data[0] = (byte) 0;
		System.arraycopy(textBytes, 0, data, 1, textBytes.length);
		NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
				NdefRecord.RTD_TEXT, new byte[0], data);
		return record;
	}

	public static boolean writeTag(NdefMessage message, Tag tag) {
		int size = message.toByteArray().length;
		try {
			Ndef ndef = Ndef.get(tag);
			if (ndef != null) {
				ndef.connect();
				if (!ndef.isWritable()) {
					return false;
				}
				if (ndef.getMaxSize() < size) {
					return false;
				}
				ndef.writeNdefMessage(message);
				return true;
			} else {
				NdefFormatable format = NdefFormatable.get(tag);
				if (format != null) {
					try {
						format.connect();
						format.format(message);
						return true;
					} catch (IOException e) {
						return false;
					}
				} else {
					return false;
				}
			}
		} catch (Exception e) {
			return false;
		}
	}

}
