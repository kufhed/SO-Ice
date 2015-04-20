package oneit.martinlutern.so_ice;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

public class Developer extends Activity {
	TextView martin, putri, kukuh, nama_dev, detail_dev;
	private CharSequence mText;
	private int mIndex;
	private long mDelay = 500;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.developer);

		martin = (TextView) findViewById(R.id.martin);
		putri = (TextView) findViewById(R.id.putri);
		kukuh = (TextView) findViewById(R.id.kukuh);
		nama_dev = (TextView) findViewById(R.id.nama_dev);
		detail_dev = (TextView) findViewById(R.id.detail_dev);

		String fontPath1 = "fonts/roboto_t.ttf";
		Typeface tf1 = Typeface.createFromAsset(getAssets(), fontPath1);
		String fontPath = "fonts/roboto_m.ttf";
		Typeface tf = Typeface.createFromAsset(getAssets(), fontPath);
		martin.setTypeface(tf1);
		putri.setTypeface(tf1);
		kukuh.setTypeface(tf1);
		nama_dev.setTypeface(tf);
		detail_dev.setTypeface(tf);

		// Add a character every 150ms
		setCharacterDelay(90);
		animateText(detail_dev.getText());
	}

	private Handler mHandler = new Handler();
	private Runnable characterAdder = new Runnable() {
		@Override
		public void run() {
			detail_dev.setText(mText.subSequence(0, mIndex++));
			if (mIndex <= mText.length()) {
				mHandler.postDelayed(characterAdder, mDelay);
			}
		}
	};

	public void animateText(CharSequence text) {
		mText = text;
		mIndex = 0;

		detail_dev.setText("");
		mHandler.removeCallbacks(characterAdder);
		mHandler.postDelayed(characterAdder, mDelay);
	}

	public void setCharacterDelay(long millis) {
		mDelay = millis;
	}

	@Override
	public void onBackPressed() {
		Intent i = new Intent(getApplicationContext(), Home.class);
		startActivity(i);
		overridePendingTransition(R.drawable.scrool_up_enter,
				R.drawable.scroll_up_exit);
		finish();
	}

}
