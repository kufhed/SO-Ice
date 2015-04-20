package oneit.martinlutern.so_ice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class Home extends Activity {
	LinearLayout btn_touch;
	Button btn_hand, btn_help, btn_developer, btn_more;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.home);

		btn_touch = (LinearLayout) findViewById(R.id.touch);
		btn_hand = (Button) findViewById(R.id.btn_hand);
		btn_help = (Button) findViewById(R.id.btn_help);
		btn_developer = (Button) findViewById(R.id.btn_developer);
		btn_more = (Button) findViewById(R.id.btn_more);

		btn_hand.setBackgroundResource(R.drawable.animation_hand);
		AnimationDrawable frameAnimation = (AnimationDrawable) btn_hand
				.getBackground();
		frameAnimation.start();

		btn_help.setVisibility(View.GONE);
		btn_developer.setVisibility(View.GONE);
		btn_more.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (btn_help.getVisibility() == View.GONE) {
					btn_help.setVisibility(View.VISIBLE);
					btn_developer.setVisibility(View.VISIBLE);
				} else {
					btn_help.setVisibility(View.GONE);
					btn_developer.setVisibility(View.GONE);
				}
			}
		});
		btn_touch.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(getApplicationContext(),
						FacePreview.class);
				startActivity(i);
				overridePendingTransition(R.drawable.scrool_up_enter,
						R.drawable.scroll_up_exit);
				finish();
			}
		});
		btn_hand.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(getApplicationContext(),
						FacePreview.class);
				startActivity(i);
				overridePendingTransition(R.drawable.scrool_up_enter,
						R.drawable.scroll_up_exit);
				finish();
			}
		});
		btn_help.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(getApplicationContext(), Help.class);
				startActivity(i);
				overridePendingTransition(R.drawable.scrool_up_enter,
						R.drawable.scroll_up_exit);
				finish();
			}
		});
		btn_developer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(getApplicationContext(), Developer.class);
				startActivity(i);
				overridePendingTransition(R.drawable.scrool_up_enter,
						R.drawable.scroll_up_exit);
				finish();
			}
		});

	}

	@Override
	public void onBackPressed() {
		new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(getString(R.string.close_apps))
				.setMessage(getString(R.string.close_apps2))
				.setPositiveButton(getString(R.string.yes),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								java.lang.System.exit(0);
								finish();
							}

						}).setNegativeButton(getString(R.string.no), null)
				.show();
	}
}
