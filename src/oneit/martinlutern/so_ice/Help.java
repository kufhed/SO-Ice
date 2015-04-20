package oneit.martinlutern.so_ice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.SurfaceView;
import android.widget.MediaController;
import android.widget.VideoView;

public class Help extends Activity {
	VideoView video_player_view;
	DisplayMetrics dm;
	SurfaceView sur_View;
	MediaController media_Controller;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.help);

		video_player_view = (VideoView) findViewById(R.id.videoView1);
		media_Controller = new MediaController(this);
		dm = new DisplayMetrics();
		this.getWindowManager().getDefaultDisplay().getMetrics(dm);
		int height = dm.heightPixels;
		int width = dm.widthPixels;
		video_player_view.setMinimumWidth(width);
		video_player_view.setMinimumHeight(height);
		video_player_view.setMediaController(media_Controller);
		video_player_view
				.setVideoPath("android.resource://oneit.martinlutern.so_ice/"
						+ R.raw.video);
		video_player_view.start();
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
