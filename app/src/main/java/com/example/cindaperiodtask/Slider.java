package com.example.cindaperiodtask;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;


public class Slider extends View {
    public Canvas mCanvas;
    private static final String TAG = "Slider";
    private static final int DIAL_ARC_STROKE_WIDTH = 22;
    private static final int THUMB_ARC_STROKE_WIDTH = 5;
    private static final int ALPHA_FOR_THUMB = 255;
    private static final int HIT_CONST = 100;
    private int mThumbReadingTextSize = 15;

    private Paint mCanvasPaint;
    private Paint mCanvasFertilePaint;
    private Paint mCanvasPeriodPaint;
    private TextPaint mTextPaint;

    private RectF mParentViewRect = new RectF();
    private RectF mArcRect = new RectF();
    private Drawable mThumbImage;
    private int mThumbColor = Color.rgb(72, 106, 176);
    private int mThumbArcStrokeWidth;
    private int mParentViewPadding;
    private int mThumbRadius;

    private Thumb mThumb;

    private float mCircleCenterX;
    private float mCircleCenterY;
    private int mCircleRadius;
    private final float mArcStartAngle = 120;
    private final float mArcSweepAngle = 300;
    private  float mArcPeriodStartAngle= 120;
    private  float mArcPeriodEndAngle= 30;

    private final float mThumbAngleLimit1 = -120;//in degrees
    private final float mThumbAngleLimit2 = -60;
    private int mStrokeWidth;
     int mMin;
     int mMax;
    private int mArcColor = -1;
    private IListenForSliderState mListener;
    private boolean mRemoveThumb = false;

    public interface IListenForSliderState {
        void onSliderMove(float reading);

        void onSliderUp(float reading);

        void onThumbSelected();
    }

    public void registerForSliderUpdates(IListenForSliderState listener) {
        mListener = listener;
    }

    public Slider(Context context) {
        this(context, null);

    }

    public Slider(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public void setPeriodAngle(float startAngle,float endAngle){
        this.mArcPeriodStartAngle= startAngle;
        this.mArcPeriodEndAngle = endAngle;
    }

    public Slider(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.slider_attributes, defStyleAttr, 0);
        initByAttributes(attributes);
        attributes.recycle();

        initPainters();
    }

    public void initByAttributes(TypedArray attributes) {

        initializeThumbs();

        this.mStrokeWidth = attributes.getDimensionPixelSize(R.styleable.slider_attributes_stroke_width, DIAL_ARC_STROKE_WIDTH);

        mParentViewPadding = attributes.getDimensionPixelSize(R.styleable.slider_attributes_background_padding, 10);

        mThumbColor = attributes.getColor(R.styleable.slider_attributes_thumb_color, 0);
        mArcColor = attributes.getColor(R.styleable.slider_attributes_arc_color, 0);
        mThumb.mThumbReadingTextColor = attributes.getColor(R.styleable.slider_attributes_thumb_text_color, 0);
        mThumbRadius = attributes.getDimensionPixelSize(R.styleable.slider_attributes_thumb_radius, 50);
        mThumbImage = attributes.getDrawable(R.styleable.slider_attributes_thumb_image);
        mThumbReadingTextSize = attributes.getDimensionPixelSize(R.styleable.slider_attributes_thumb_reading_text_size, mThumbReadingTextSize);

        mMin = attributes.getInteger(R.styleable.slider_attributes_min, 1);
        mMax = attributes.getInteger(R.styleable.slider_attributes_max, 30);
        int thumbReading = attributes.getInteger(R.styleable.slider_attributes_curr_thumb_reading, mMin);

        mThumbArcStrokeWidth = dpToPixels(THUMB_ARC_STROKE_WIDTH);

        setThumbReading(thumbReading);
    }

    private void initPainters() {
        initCanvasPaint();
        initTextPaint();
        setLayerType(LAYER_TYPE_SOFTWARE, mCanvasPaint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
mCanvas = canvas;
        drawArc(canvas);
        drawFertileWindow(canvas);
        drawPeriodWindow(canvas);
        if (!mRemoveThumb) {
            drawThumb(canvas, mThumb);
        }
    }

    public Canvas getCanvas(){
        return mCanvas;
    }

    private void drawFertileWindow(Canvas canvas) {
        setPaintPropertiesForFertile();
        canvas.drawArc(mArcRect, 250, 80, false, mCanvasFertilePaint);
    }

    private void drawPeriodWindow(Canvas canvas) {
        setPaintPropertiesForPeriod();
        canvas.drawArc(mArcRect, mArcPeriodStartAngle, mArcPeriodEndAngle, false, mCanvasPeriodPaint);
    }

    private void drawArc(Canvas canvas) {
        setPaintPropertiesForDialArc();
        mCanvasPaint.setColor(getArcColor());
        canvas.drawArc(mArcRect, mArcStartAngle, mArcSweepAngle, false, mCanvasPaint);
    }

    private void drawThumb(Canvas canvas, Thumb thumb) {

        // find thumb position
        //sin(-veAngle) = -1*sin(ofThatAngle)
        //However for cosine its always positive
        thumb.mX = (int) (mCircleCenterX + (mCircleRadius + mStrokeWidth + mThumbRadius / 2) * Math.cos(thumb.mThumbAngle));
        //since Sine is +1 near the android's coordinate system we are reversing it.
        //Sine increases as you go up the screen however in android's screen coordinate system your y-coordinate decreases
        thumb.mY = (int) (mCircleCenterY - (mCircleRadius + mStrokeWidth + mThumbRadius / 2) * Math.sin(thumb.mThumbAngle)); //coz we are manually reversing the ThumbAngle if the user is dragging below the origin

        if (thumb.mOldThumbX == -1 || thumb.mOldThumbY == -1) {
            recordOldState();
        }

        if (mThumbImage != null) {
            // draw png
            mThumbImage.setBounds(thumb.mX - mThumbRadius / 2, thumb.mY - mThumbRadius / 2, thumb.mX + mThumbRadius / 2, thumb.mY + mThumbRadius / 2);
            mThumbImage.draw(canvas);
        } else {

            setTextPaintProperties(thumb);

            //maintaining the old thumb (There is no need for old thumb in this use case)
//            if (thumb.mOldThumbAngle != thumb.mThumbAngle && thumb.mIsThumbSelected) {
//                drawTearDropShapedThumb(canvas, thumb.mOldThumbX, thumb.mOldThumbY, mThumbRadius, thumb.mOldThumbAngle);
//
//                updateTextInsideTheThumb(thumb.mOldThumbX, thumb.mOldThumbY, canvas, Math.round(thumb.mLastReading));
//
//               // drawArcBetweenThumbs(canvas, thumb);
//            }
            /*
                Draws the future setPoint thumb.
                From the thumbAngle, you can derive the point from where the lines needs to be drawn for the tear drop shape.
             */
            drawTearDropShapedThumb(canvas, thumb.mX, thumb.mY, mThumbRadius, thumb.mThumbAngle);

            updateTextInsideTheThumb(thumb.mX, thumb.mY, canvas, Math.round(thumb.mReading));
        }
    }

    private void setTextPaintProperties(Thumb thumb) {
        mTextPaint.reset();
        mTextPaint.setColor(thumb.mThumbReadingTextColor);
        mTextPaint.setTextSize(mThumbReadingTextSize);
        mTextPaint.setAntiAlias(true);
    }

    private void setPaintPropertiesForFertile() {
        mCanvasFertilePaint.reset();
        mCanvasFertilePaint.setColor(Color.rgb(248,178,154));
        mCanvasFertilePaint.setStyle(Paint.Style.STROKE);
        mCanvasFertilePaint.setStrokeWidth(mStrokeWidth + 110);
        mCanvasFertilePaint.setAntiAlias(true);
        mCanvasFertilePaint.setAlpha(ALPHA_FOR_THUMB);
    }

    private void setPaintPropertiesForPeriod() {
        mCanvasPeriodPaint.reset();
        mCanvasPeriodPaint.setColor(Color.rgb(235,61,59));
        mCanvasPeriodPaint.setStyle(Paint.Style.STROKE);
        mCanvasPeriodPaint.setStrokeWidth(mStrokeWidth + 110);
        mCanvasPeriodPaint.setAntiAlias(true);
        mCanvasPeriodPaint.setAlpha(ALPHA_FOR_THUMB);
    }

    private void setPaintPropertiesForThumb() {
        mCanvasPaint.reset();
        mCanvasPaint.setColor(mThumbColor);
        mCanvasPaint.setStyle(Paint.Style.FILL);
        mCanvasPaint.setAntiAlias(true);
        mCanvasPaint.setAlpha(ALPHA_FOR_THUMB);
    }

    private void updateTextInsideTheThumb(int thumbX, int thumbY, Canvas canvas, int reading) {
        String text = "Day " + String.valueOf(reading);

        float horizontalAdjustmentFactor;
        if (text.length() == 1) {
            horizontalAdjustmentFactor = 0.35f;
        } else if (text.length() <= 2) {
            horizontalAdjustmentFactor = 0.65f;
        } else {
            horizontalAdjustmentFactor = 0.75f;
        }

        canvas.rotate(180, thumbX ,thumbY);
        canvas.drawText(text, thumbX - (mThumbRadius * horizontalAdjustmentFactor), thumbY + (mThumbRadius / 3), mTextPaint);
    }

    /*
       y - y1 = m(x - x1); equation of a line.
       The below equation you get by taking the ratio of the distance and then substituting the above equation into that ratio
       equation and then getting one of the coordinates and then substituting that in the above coordinate to get the other point.
    */
    private Coordinates getPointOnTheLineAtDistance(Coordinates p1, Coordinates p2, int distanceFromPoint) {
        float distanceBetweenPoints = (float) Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2));
        float t = distanceFromPoint / distanceBetweenPoints;
        Coordinates future = new Coordinates();
        future.x = ((1 - t) * p1.x + t * p2.x);
        future.y = ((1 - t) * p1.y + t * p2.y);
        return future;
    }

    private void drawTearDropShapedThumb(Canvas canvas, int thumbX, int thumbY, int thumbRadius, double thumbAngleInRadians) {
        setPaintPropertiesForThumb();

        float openingAngle = 60.0f;
        float arcSweepAngle = 270.0f;
        Path path = new Path();

        RectF pathRect = new RectF(thumbX - thumbRadius, thumbY - thumbRadius, thumbX + thumbRadius, thumbY + thumbRadius);

        double startAngle = getAngleInDegrees(thumbAngleInRadians);

        //You determine the tilt angle by reversing the thumb angle by 180 and then starting the arc by openingAngle / 2.
        startAngle = (180 - startAngle) + openingAngle / 2;
        path.addArc(pathRect, (float) startAngle, arcSweepAngle);
        Coordinates endPointOfCone = getPointOnTheLineAtDistance(new Coordinates(thumbX, thumbY), new Coordinates(mCircleCenterX, mCircleCenterY), thumbRadius + thumbRadius / 2);
        path.lineTo(endPointOfCone.x, endPointOfCone.y);
        path.close();
        //to get the shadow correct.
        mCanvasPaint.setShadowLayer(thumbRadius / 4, 0.0f, 0.0f, Color.parseColor("#80454545"));
        canvas.drawPath(path, mCanvasPaint);
    }

    //called after the view's size is determined.
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {

        mArcRect.set(mParentViewRect.left + mParentViewPadding, mParentViewRect.top + mParentViewPadding,
                mParentViewRect.right - mParentViewPadding, mParentViewRect.bottom - mParentViewPadding);

        // use smaller dimension for calculations (depends on parent size)
        mCircleCenterX = mArcRect.centerX();
        mCircleCenterY = mArcRect.centerY();
        mCircleRadius = (int) (mArcRect.width() < mArcRect.height() ? mArcRect.width() / 2 : mArcRect.height() / 2);

        super.onSizeChanged(w, h, oldw, oldh);
    }

    /**
     * Invoked when slider starts moving or is currently moving. This method calculates and sets position and angle of the thumb.
     *
     * @param touchX Where is the touch identifier now on X axis
     * @param touchY Where is the touch identifier now on Y axis
     *               <p/>
     *               This update happens in the range of -180 to 0 to 180.
     */
    private void updateSliderState(Thumb thumb, int touchX, int touchY) {

        float distanceX = touchX - mCircleCenterX;
        float distanceY = mCircleCenterY - touchY; //as you go down the screen the Y coordinate increases.

        double radius = Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));
        double angle = Math.acos(distanceX / radius);
        if (distanceY < 0) {
            angle = -angle;
        }


        updateSliderBy(thumb, angle);
    }

    private void updateSliderBy(Thumb thumb, double angle) {

        if (isValidAngle(angle)) {
            thumb.mThumbAngle = angle;
        } else {
            return;
        }

        thumb.mReading = valueOnTheSlider(thumb.mThumbAngle);
        if (mListener != null) {
            mListener.onSliderMove(thumb.mReading);
        }
    }

    //gets the value of the slider from the thumb angle.
    private int valueOnTheSlider(double thumbAngle) {
        int offsetFromStart = (int) Math.round(((getPaintSweepNormalizedAngle(thumbAngle) - mArcStartAngle) * getStepSizePerDegree()));
        return mMin + offsetFromStart;
    }

    //gets the angle from the reading.
    private double getAngleFromReading(double reading) {
        if (reading == mMin) {
            return mThumbAngleLimit1; //since we know mArcStartAngle is in the sweep angle format.
        } else if (reading == mMax) {
            return mThumbAngleLimit2;
        } else {
            float stepsFromMinReading = (float) (reading - mMin);
            //stepsFromMinReading / getStepSizePerDegree() returns an angle that is in the paintSweep angle mode, convert that into the normal quadrant mode angle.
            double offsetAngle = (1 / getStepSizePerDegree()) * stepsFromMinReading;
            double finalAngleInDegrees = mArcStartAngle + offsetAngle;
            return 360 - finalAngleInDegrees;
        }
    }

    /*
     * This tries to put the angle in 0 to 360 mode.
     * Since the sweep angle goes from 0 to 360 and in the clockwise direction.
     * Takes the angle in radians.
     *
     * input angle is in human coordinate system.
     * output angle is in Android sweep angle coordinate system.
     * */
    private double getPaintSweepNormalizedAngle(double angle) {
        double normalizedAngleInDegrees = 0;

        //TODO: Is there a way to get rid of quadrant here?
        //-110 degrees
        if (getAngleInDegrees(angle) < 0 && (Math.abs(getAngleInDegrees(angle)) > 90)) {
            //the 3rd quadrant i.e. the start arc quadrant
            //if its negative, we make it positive jus to match the start angle.
            normalizedAngleInDegrees = Math.abs(getAngleInDegrees(angle));
        } else if (getAngleInDegrees(angle) < 0 && (Math.abs(getAngleInDegrees(angle)) < 90)) {
            //4th quadrant, -80degrees
            normalizedAngleInDegrees = (360 + Math.abs(getAngleInDegrees(angle)));
        } else {
            //anywhere in the first two quadrants.
            normalizedAngleInDegrees = (360 - getAngleInDegrees(angle));
        }

        return normalizedAngleInDegrees;
    }

    private double getStepSizePerDegree() {
        return (mMax - mMin) / mArcSweepAngle;
    }

    private double getAngleInDegrees(double angleInRadians) {
        return Math.toDegrees(angleInRadians);
    }

    private double getAngleInRadians(double angleInDegrees) {
        return Math.toRadians(angleInDegrees);
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (mRemoveThumb) {
            return true;
        }
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                //if we dont have a thumb
                // start moving the thumb (this is the first touch)
                int x = (int) motionEvent.getX();
                int y = (int) motionEvent.getY();
                //making sure the user has touched the thumb

                if (x < mThumb.mX + HIT_CONST && x > mThumb.mX - HIT_CONST && y < mThumb.mY + HIT_CONST && y > mThumb.mY - HIT_CONST) {
                    if (mThumb.mIsThumbEnabled) {
                        recordOldState();
                        setThumbSelected(true);
                        invalidate();
                    }
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                //to move action down event will happen first and hence one of the thumbs should get selected and we move only then
                if (mThumb.mIsThumbSelected) {
                    onSliderActionMove(mThumb, motionEvent);
                    invalidate();
                }
                break;
            }

            case MotionEvent.ACTION_UP: {
                //when action up event happens, wait for commit command present inside the Consumer to call invalidate
                if (mThumb.mIsThumbSelected && isValidAngle(mThumb.mThumbAngle)) {
                    reportSliderState();
                    setThumbSelected(false);
                    invalidate();
                }
                break;
            }
        }

        return true;
    }

    private void reportSliderState() {
        if (mThumb.mIsThumbSelected) {
            if (mListener != null) {
                mListener.onSliderUp(mThumb.mReading);
            }
        }
    }

    private void setThumbSelected(boolean selected) {
        if (mThumb.mIsThumbEnabled) {
            mThumb.mIsThumbSelected = selected;
            if (mListener != null && selected) {
                mListener.onThumbSelected();
            }
        }
    }

    private void onSliderActionMove(Thumb thumb, MotionEvent motionEvent) {
        int x = (int) motionEvent.getX();
        int y = (int) motionEvent.getY();
        updateSliderState(thumb, x, y);
    }

    private void recordOldState() {
        mThumb.mOldThumbAngle = mThumb.mThumbAngle;
        mThumb.mOldThumbX = mThumb.mX;
        mThumb.mOldThumbY = mThumb.mY;
        mThumb.mLastReading = mThumb.mReading;
    }

    public void setThumbAngle(Thumb thumb, double angleInDegrees) {
        double thumbAngle = getAngleInRadians(angleInDegrees);
        thumb.mThumbAngle = thumbAngle;
        thumb.mOldThumbAngle = thumbAngle;
    }

    /*this angle calculation is in radians
        And it goes between 0 to 180 and 0 to -180.
     */
    boolean isValidAngle(double angle) {
        boolean isValid = false;
        double limit1 = getArcLimit1(); //-2.09
        //covers the arc in the third quadrant where the angle goes from -90 to -180
        if (angle <= limit1 && angle >= -Math.PI) {
            isValid = true;
        }
        double limit2 = getArcLimit2();//-0.916
        if (angle < Math.PI && angle >= limit2) {
            isValid = true;
        }

        return isValid;
    }

    //theta / 360 * 2*PI*R
    private double getArcLimit2() {
        return -((360 - mArcSweepAngle) * Math.PI) / 180;
    }

    private double getArcLimit1() {
        return -(mArcStartAngle * Math.PI) / 180;
    }

    private int getArcColor() {
        return mArcColor;
    }

    public void setRemoveThumb(boolean removeThumb) {
        mRemoveThumb = removeThumb;
    }

    public void setThumbReading(float reading) {
        mThumb.mReading = reading;
        mThumb.mLastReading = reading;
        setThumbAngle(mThumb, getAngleFromReading(mThumb.mReading));
    }

    private class Coordinates {
        float x;
        float y;

        public Coordinates() {
        }

        public Coordinates(float xCord, float yCord) {
            x = xCord;
            y = yCord;
        }
    }

    private void initCanvasPaint() {
        if (mCanvasPaint == null) {
            mCanvasPaint = new Paint();
        }
        if (mCanvasFertilePaint == null) {
            mCanvasFertilePaint = new Paint();
        }
        if (mCanvasPeriodPaint == null) {
            mCanvasPeriodPaint = new Paint();
        }
        setPaintPropertiesForDialArc();
    }

    private void setPaintPropertiesForDialArc() {
        mCanvasPaint.reset();
        mCanvasPaint.setStrokeWidth(mStrokeWidth);
        mCanvasPaint.setStyle(Paint.Style.STROKE);
        mCanvasPaint.setStrokeCap(Paint.Cap.SQUARE);
        mCanvasPaint.setAntiAlias(true);
    }


    private void initTextPaint() {
        if (mTextPaint == null) {
            mTextPaint = new TextPaint();
        }
        mTextPaint.reset();
        mTextPaint.setTextSize(48);
        mTextPaint.setAntiAlias(true);
    }

    private int dpToPixels(int dpValue) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue, metrics));
    }

    private void initializeThumbs() {
        mThumb = new Thumb();
        mThumb.reset();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //need at least this much
        int minWidth = 100;
        int minHeight = 100;

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        //Measure Width
        if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            width = Math.min(minWidth, widthSize);
        } else {
            //Be whatever you want
            width = minWidth;
        }

        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = Math.min(minHeight, heightSize);
        } else {
            //Be whatever you want
            height = minHeight;
        }

        //left bottom diagonal and right top diagonal coordinates.
        mParentViewRect.set(mStrokeWidth / 2f, mStrokeWidth / 2f, width - mStrokeWidth / 2f, MeasureSpec.getSize(heightMeasureSpec) - mStrokeWidth / 2f);

        //MUST CALL THIS
        setMeasuredDimension(width, height);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        initCanvasPaint();
        initTextPaint();
    }

    private class Thumb {
        //the following two values gets updated everytime the thumb moves.
        private int mX = -1;
        private int mY = -1;
        //the following two values gets updated only when we get a dataupdate and we show a single thumb.
        private int mOldThumbX = -1;
        private int mOldThumbY = -1;
        private double mOldThumbAngle = 0;

        private double mThumbAngle = 0;

        private float mLastReading = 0;
        private float mReading = 0;

        private int mThumbReadingTextColor;// this is also used to set the color of the arc between the thumbs.

        private boolean mIsThumbSelected = false;
        public boolean mIsThumbEnabled = true;

        public void reset() {
            mX = -1;
            mY = -1;
            mOldThumbAngle = 0;
            mOldThumbX = -1;
            mOldThumbY = -1;
            mThumbAngle = 0;
            mLastReading = 0;
            mReading = 0;
            mIsThumbEnabled = true;
        }
    }
}