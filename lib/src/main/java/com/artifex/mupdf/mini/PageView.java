package com.artifex.mupdf.mini;

import com.artifex.mupdf.fitz.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Scroller;
import android.content.res.Configuration;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;

public class PageView extends View implements
	GestureDetector.OnGestureListener,
	ScaleGestureDetector.OnScaleGestureListener
{
	private final String APP = "MuPDF";

	protected DocumentActivity actionListener;

	protected float pageScale, viewScale, minScale, maxScale;
	protected Bitmap bitmap;
	protected int bitmapW, bitmapH;
	protected int canvasW, canvasH;
	protected int scrollX, scrollY;
	protected Rect[] linkBounds;
	protected String[] linkURIs;
	protected Quad[][] hits;
	protected boolean showLinks;

	protected GestureDetector detector;
	protected ScaleGestureDetector scaleDetector;
	protected Scroller scroller;
	protected boolean error;
	protected Paint errorPaint;
	protected Path errorPath;
	protected Paint linkPaint;
	protected Paint hitPaint;

    protected boolean isDarkMode;

	public PageView(Context ctx, AttributeSet atts) {
		super(ctx, atts);

		scroller = new Scroller(ctx);
		detector = new GestureDetector(ctx, this);
		scaleDetector = new ScaleGestureDetector(ctx, this);

		pageScale = 1;
		viewScale = 1;
		minScale = 1;
		maxScale = 8;

		linkPaint = new Paint();
		linkPaint.setARGB(32, 0, 0, 255);

		hitPaint = new Paint();
		hitPaint.setARGB(32, 255, 0, 0);
		hitPaint.setStyle(Paint.Style.FILL);

		errorPaint = new Paint();
		errorPaint.setARGB(255, 255, 80, 80);
		errorPaint.setStrokeWidth(5);
		errorPaint.setStyle(Paint.Style.STROKE);

		errorPath = new Path();
		errorPath.moveTo(-100, -100);
		errorPath.lineTo(100, 100);
		errorPath.moveTo(100, -100);
		errorPath.lineTo(-100, 100);

        isDarkMode = isDarkMode(ctx);
	}

    private static boolean isDarkMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

	public void setActionListener(DocumentActivity l) {
		actionListener = l;
	}

	public synchronized void setError() {
		if (bitmap != null)
			bitmap.recycle();
		error = true;
		linkBounds = new Rect[0];
		linkURIs = new String[0];
		hits = null;
		bitmap = null;
		scroller.forceFinished(true);
		invalidate();
	}

	public synchronized void setBitmap(Bitmap b, float zoom, boolean wentBack, Rect[] lbs, String[] lus, Quad[][] hs) {
		if (bitmap != null)
			bitmap.recycle();
		error = false;
		linkBounds = lbs;
		linkURIs = lus;
		hits = hs;
		bitmap = b;
		bitmapW = (int)(bitmap.getWidth() * viewScale / zoom);
		bitmapH = (int)(bitmap.getHeight() * viewScale / zoom);
		scroller.forceFinished(true);
		if (pageScale == zoom) {
			scrollX = wentBack ? bitmapW - canvasW : 0;
			scrollY = wentBack ? bitmapH - canvasH : 0;
		}
		pageScale = zoom;
		invalidate();
	}

	public void resetHits() {
		hits = null;
		invalidate();
	}

	public void onSizeChanged(int w, int h, int ow, int oh) {
		canvasW = w;
		canvasH = h;
		if (actionListener != null)
			actionListener.onPageViewSizeChanged(w, h);
	}

	public boolean onTouchEvent(MotionEvent event) {
		detector.onTouchEvent(event);
		scaleDetector.onTouchEvent(event);
		return true;
	}

	public boolean onDown(MotionEvent e) {
		scroller.forceFinished(true);
		return true;
	}

	public void onShowPress(MotionEvent e) { }

	public void onLongPress(MotionEvent e) {
		showLinks = !showLinks;
		invalidate();
	}

	public boolean onSingleTapUp(MotionEvent e) {
		boolean foundLink = false;
		float x = e.getX();
		float y = e.getY();
		if (showLinks && linkBounds != null) {
			float dx = (bitmapW <= canvasW) ? (bitmapW - canvasW) / 2 : scrollX;
			float dy = (bitmapH <= canvasH) ? (bitmapH - canvasH) / 2 : scrollY;
			float mx = (x + dx) / viewScale;
			float my = (y + dy) / viewScale;
			for (int i = 0; i < linkBounds.length; i++) {
				Rect b = linkBounds[i];
				if (mx >= b.x0 && mx <= b.x1 && my >= b.y0 && my <= b.y1) {
					if (Link.isExternal(linkURIs[i]) && actionListener != null)
						actionListener.gotoURI(linkURIs[i]);
					else if (actionListener != null)
						actionListener.gotoPage(linkURIs[i]);
					foundLink = true;
					break;
				}
			}
		}
		if (!foundLink) {
			float a = canvasW / 3;
			float b = a * 2;
			if (x <= a) goBackward();
			if (x >= b) goForward();
			if (x > a && x < b && actionListener != null) actionListener.toggleUI();
		}
		invalidate();
		return true;
	}

	public synchronized boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
		if (bitmap != null) {
			scrollX += (int)dx;
			scrollY += (int)dy;
			scroller.forceFinished(true);
			invalidate();
		}
		return true;
	}

	public synchronized boolean onFling(MotionEvent e1, MotionEvent e2, float dx, float dy) {
		if (bitmap != null) {
			int maxX = bitmapW > canvasW ? bitmapW - canvasW : 0;
			int maxY = bitmapH > canvasH ? bitmapH - canvasH : 0;
			scroller.forceFinished(true);
			scroller.fling(scrollX, scrollY, (int)-dx, (int)-dy, 0, maxX, 0, maxY);
			invalidate();
		}
		return true;
	}

	public boolean onScaleBegin(ScaleGestureDetector det) {
		return true;
	}

	public synchronized boolean onScale(ScaleGestureDetector det) {
		if (bitmap != null) {
			float focusX = det.getFocusX();
			float focusY = det.getFocusY();
			float scaleFactor = det.getScaleFactor();
			float pageFocusX = (focusX + scrollX) / viewScale;
			float pageFocusY = (focusY + scrollY) / viewScale;
			viewScale *= scaleFactor;
			if (viewScale < minScale) viewScale = minScale;
			if (viewScale > maxScale) viewScale = maxScale;
			bitmapW = (int)(bitmap.getWidth() * viewScale / pageScale);
			bitmapH = (int)(bitmap.getHeight() * viewScale / pageScale);
			scrollX = (int)(pageFocusX * viewScale - focusX);
			scrollY = (int)(pageFocusY * viewScale - focusY);
			scroller.forceFinished(true);
			invalidate();
		}
		return true;
	}

	public void onScaleEnd(ScaleGestureDetector det) {
		if (actionListener != null)
			actionListener.onPageViewZoomChanged(viewScale);
	}

	public void goBackward() {
		scroller.forceFinished(true);
		if (scrollY <= 0) {
			if (scrollX <= 0) {
				if (actionListener != null)
					actionListener.goBackward();
				return;
			}
			scroller.startScroll(scrollX, scrollY, -canvasW * 9 / 10, bitmapH - canvasH - scrollY, 500);
		} else {
			scroller.startScroll(scrollX, scrollY, 0, -canvasH * 9 / 10, 250);
		}
		invalidate();
	}

	public void goForward() {
		scroller.forceFinished(true);
		if (scrollY + canvasH >= bitmapH) {
			if (scrollX + canvasW >= bitmapW) {
				if (actionListener != null)
					actionListener.goForward();
				return;
			}
			scroller.startScroll(scrollX, scrollY, canvasW * 9 / 10, -scrollY, 500);
		} else {
			scroller.startScroll(scrollX, scrollY, 0, canvasH * 9 / 10, 250);
		}
		invalidate();
	}

	private android.graphics.Rect dst = new android.graphics.Rect();
	private Path path = new Path();

	public synchronized void onDraw(Canvas canvas) {
		int x, y;

		if (bitmap == null) {
			if (error) {
				canvas.translate(canvasW / 2, canvasH / 2);
				canvas.drawPath(errorPath, errorPaint);
			}
			return;
		}

		if (scroller.computeScrollOffset()) {
			scrollX = scroller.getCurrX();
			scrollY = scroller.getCurrY();
			invalidate(); /* keep animating */
		}

		if (bitmapW <= canvasW) {
			scrollX = 0;
			x = (canvasW - bitmapW) / 2;
		} else {
			if (scrollX < 0) scrollX = 0;
			if (scrollX > bitmapW - canvasW) scrollX = bitmapW - canvasW;
			x = -scrollX;
		}

		if (bitmapH <= canvasH) {
			scrollY = 0;
			y = (canvasH - bitmapH) / 2;
		} else {
			if (scrollY < 0) scrollY = 0;
			if (scrollY > bitmapH - canvasH) scrollY = bitmapH - canvasH;
			y = -scrollY;
		}

		dst.set(x, y, x + bitmapW, y + bitmapH);
		if (!isDarkMode) {
			canvas.drawBitmap(bitmap, null, dst, null);
		} else {
			ColorMatrix colorMatrix = new ColorMatrix();
			colorMatrix.set(new float[] {
				-1.0f, 0, 0, 0, 255,
				 0, -1.0f, 0, 0, 255,
				 0, 0, -1.0f, 0, 255,
				 0, 0, 0, 1.0f, 0
			});

			ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
			Paint paint = new Paint();
			paint.setColorFilter(colorFilter);
			canvas.drawBitmap(bitmap, null, dst, paint);
		}

		if (showLinks && linkBounds != null) {
			for (Rect b : linkBounds) {
				canvas.drawRect(
					x + b.x0 * viewScale,
					y + b.y0 * viewScale,
					x + b.x1 * viewScale,
					y + b.y1 * viewScale,
					linkPaint
				);
			}
		}

		if (hits != null && hits.length > 0) {
			for (Quad[] h : hits)
				for (Quad q : h) {
					path.rewind();
					path.moveTo(x + q.ul_x * viewScale, y + q.ul_y * viewScale);
					path.lineTo(x + q.ll_x * viewScale, y + q.ll_y * viewScale);
					path.lineTo(x + q.lr_x * viewScale, y + q.lr_y * viewScale);
					path.lineTo(x + q.ur_x * viewScale, y + q.ur_y * viewScale);
					path.close();
					canvas.drawPath(path, hitPaint);
				}
		}
	}
}
