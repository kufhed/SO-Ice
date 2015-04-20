package oneit.martinlutern.so_ice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class Splash_App extends Activity {

	private static int SPLASH_TIME_OUT = 3000;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.splash_app);

		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				// This method will be executed once the timer is over
				check(Splash_App.this);
			}
		}, SPLASH_TIME_OUT);

	}

	public void check(Context ctx) {
		Intent i;
		i = new Intent(getApplicationContext(), Home.class);
		// close this activity
		startActivity(i);
		overridePendingTransition(R.drawable.scrool_up_enter,
				R.drawable.scroll_up_exit);
		finish();
	}
}
