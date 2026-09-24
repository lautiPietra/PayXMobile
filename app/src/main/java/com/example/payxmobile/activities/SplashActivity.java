package com.example.payxmobile.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.utils.SesionUtils;
import com.example.payxmobile.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView imgLogo = findViewById(R.id.imgLogo);

        imgLogo.post(() -> animateLogo(imgLogo));
    }

    private void animateLogo(ImageView imgLogo) {
        int width = imgLogo.getWidth();
        int height = imgLogo.getHeight();

        // Empieza con el logo completamente oculto
        imgLogo.setClipBounds(new Rect(0, 0, 0, height));

        ValueAnimator revealAnimator = ValueAnimator.ofFloat(0f, 1f);
        revealAnimator.setDuration(2600);
        revealAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        revealAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            imgLogo.setClipBounds(new Rect(0, 0, (int) (width * progress), height));
        });
        revealAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                imgLogo.setClipBounds(null);
                new Handler(Looper.getMainLooper()).postDelayed(() -> fadeOutAndNavigate(imgLogo), 800);
            }
        });
        revealAnimator.start();
    }

    private void fadeOutAndNavigate(ImageView imgLogo) {
        imgLogo.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> {
                    // Con token vigente se entra directo; vencido (según su "exp", sin llamar al
                    // backend) se limpia la sesión y se avisa en el Login.
                    SessionManager session = new SessionManager(SplashActivity.this);
                    Intent destino;
                    if (session.tieneSesionVigente()) {
                        destino = new Intent(SplashActivity.this, HomeActivity.class);
                    } else {
                        destino = new Intent(SplashActivity.this, LoginActivity.class);
                        if (session.getToken() != null) {
                            destino.putExtra(SesionUtils.EXTRA_MENSAJE, SesionUtils.MSG_SESION_VENCIDA);
                        }
                        session.clearSession();
                    }
                    startActivity(destino);
                    finish();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                })
                .start();
    }
}
