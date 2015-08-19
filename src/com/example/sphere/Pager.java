package com.example.sphere;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import java.util.ArrayList;
import java.util.List;

public class Pager extends View {
    private int mWidth = 1280;
    private int mHeight = 768;
    private float mRadius = 0;
    private List<ImagePiece> curImagePieces;
    private boolean isSplitCurImage = true;
    private List<ImagePiece> nextImagePieces;
    private boolean isSplitNextImage = true;
    private int mRows = 6;
    private int mColumns = 6;
    private float[] oldCoordX;
    private float[] oldCoordY;
    float[] coordZ1;
    float[] rotateX;
    float[] coordY;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private float deltaDistance;
    private boolean isSetCoord = false;
    //是否优先画当前页
    private boolean isCurrentFirst = false;
    //画笔
    private Paint mPaint;
    //拖拽点
    PointF mTouch = new PointF();
    //第一次拖拽的点
    PointF mFirstTouch = new PointF();

    Bitmap mCurPageBitmap = null;
    Bitmap mNextPageBitmap = null;

    Scroller mScroller;
    Camera mCamera;
    Matrix mMatrix;
    ColorMatrixColorFilter mColorMatrixFilter;

    public Pager(Context context, int screenWidth, int screenHeight) {
        super(context);
        this.mWidth = screenWidth;
        this.mHeight = screenHeight;
        mScroller = new Scroller(getContext());
        mCamera = new Camera();
        mMatrix = new Matrix();
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setFilterBitmap(true);
        ColorMatrix cm = new ColorMatrix();
        float array[] = {
                0.4f,   0,      0,      0,      100,
                0,      0.4f,   0,      0,      100,
                0,      0,      0.4f,   0,      100,
                0,      0,      0,      1.0f,   0
        };
        cm.set(array);
        mColorMatrixFilter = new ColorMatrixColorFilter(cm);
    }

    /**
     * bitmap单位
     */
    private class ImagePiece {
        public int index = 0;
        public Bitmap bitmap = null;
    }

    /**
     * 切割bitmap
     *
     * @param bitmap
     * @param row
     * @param column
     * @return
     */
    private List<ImagePiece> split(Bitmap bitmap, int row, int column) {
        List<ImagePiece> pieces = new ArrayList<ImagePiece>(row * column);

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int pieceWidth = width / column;
        int pieceHeight = height / row;

        for (int i = 0; i < row; i++) {
            for (int j = 0; j < column; j++) {
                ImagePiece imagePiece = new ImagePiece();
                imagePiece.index = j + i * column;

                int xValue = j * pieceWidth;
                int yValue = i * pieceHeight;

                imagePiece.bitmap = Bitmap.createBitmap(bitmap, xValue, yValue,
                        pieceWidth, pieceHeight);
                pieces.add(imagePiece);
            }
        }
        return pieces;
    }

    /**
     * 初期化数据
     */
    private void initData() {
        oldCoordX = new float[mRows * mColumns];
        oldCoordY = new float[mRows * mColumns];

        /**
         * 计算球的半径
         */
        float radius = (getMeasuredHeight() - getMeasuredHeight() / mRows) / 2.0f;
        mRadius = radius;

        /**
         * 保证每个bitmap是个正方形，避免bitmap产生重叠（不好看）
         * 计算每个bitmap的宽或高应该缩小多少
         */
        if(getMeasuredHeight() / mRows > getMeasuredWidth() / mColumns){
            scaleX = 1.0f;
            scaleY = ((float)getMeasuredWidth() / mColumns) / ((float)getMeasuredHeight() / mRows);
        }else{
            scaleX = ((float)getMeasuredHeight() / mRows) / ((float)getMeasuredWidth() / mColumns);
            scaleY = 1.0f;
        }

        /**
         * 获取z轴坐标
         * 获取绕x轴旋转多少度
         * 获取y轴坐标
         */
        coordZ1 = getCoordZ1();
        rotateX = getRotateX();
        coordY = getCoordY();

        /**
         * 计算bitmap现在所处的坐标
         */
        if (curImagePieces != null && curImagePieces.size() > 0) {
            for (int i = 0; i < mRows; i++) {
                for (int j = 0; j < mColumns; j++) {
                    oldCoordX[i * mColumns + j] = getPieceBitmapX(i, j);
                    oldCoordY[i * mColumns + j] = getPieceBitmapY(i, j);
                }
            }
        }
    }

    private float getPieceBitmapX(int row, int col) {
        float x = 0.0f;
        for (int i = 0; i < col; i++) {
            x += curImagePieces.get(row * mColumns + i).bitmap.getWidth();
        }
        return x;
    }

    private float getPieceBitmapY(int row, int col) {
        float y = 0.0f;
        for (int i = 0; i < row; i++) {
            y += curImagePieces.get(i * mColumns + col).bitmap.getHeight();
        }
        return y;
    }

    /**
     * bitmap所在的x坐标
     * @param startAngle
     * @return
     */
    private float[] getCoordX(double startAngle){
        float[] coordX = new float[mRows * mColumns];
        double angle = 180.0f / (mColumns * 2) * Math.PI / 180;
        float centerX = getMeasuredWidth() / 2.0f - getMeasuredWidth() / mColumns / 2;

        for(int i = 0;i < mRows;i++){
            for(int j = 0;j < mColumns;j++){
                /**
                 * x        :   centerX - cos α * R
                 * y        :   centerY - cos α * R
                 * z        :   centerY - sin α * R
                 * rotateX  :   90 - α
                 */
                double realAngle = 2 * j * angle + angle + startAngle * Math.PI / 180;
                coordX[i * mColumns + j] = (float) (centerX - Math.cos(realAngle) * mRadius);
            }
        }

        return coordX;
    }

    /**
     * bitmap所在的y坐标
     * @return
     */
    private float[] getCoordY(){
        float[] coordY = new float[mRows * mColumns];
        double angle = 180.0f / (mRows * 2) * Math.PI / 180;
        float centerY = getMeasuredHeight() / 2.0f - getMeasuredHeight() / mRows / 2;
        for(int i = 0;i < mRows;i++){
            for(int j = 0;j < mColumns;j++){
                /**
                 * x        :   centerX - cos α * R
                 * y        :   centerY - cos α * R
                 * z        :   centerY - sin α * R
                 * rotateX  :   90 - α
                 */
                double realAngle = 2 * i * angle + angle;
                coordY[i * mColumns + j] = (float) (centerY - Math.cos(realAngle) * mRadius);
            }
        }
        return coordY;
    }

    /**
     * bitmap相对于y轴的bitmap的z轴上的距离
     * @return
     */
    private float[] getCoordZ1(){
        float[] coordZ = new float[mRows * mColumns];
        double angle = 180.0f / (mRows * 2) * Math.PI / 180;
        float centerY = getMeasuredHeight() / 2.0f - getMeasuredHeight() / mRows / 2;
        for(int i = 0;i < mRows;i++){
            for(int j = 0;j < mColumns;j++){
                /**
                 * x        :   centerX - cos α * R
                 * y        :   centerY - cos α * R
                 * z        :   centerY - sin α * R
                 * rotateX  :   90 - α
                 */
                double realAngle1 = 2 * i * angle + angle;
                coordZ[i * mColumns + j] = (float) (centerY - Math.sin(realAngle1) * mRadius);
            }
        }
        return coordZ;
    }

    /**
     * bitmap相对于x轴的bitmap的z轴上的距离
     * @param startAngle
     * @return
     */
    private float[] getCoordZ2(double startAngle){
        float[] coordZ = new float[mRows * mColumns];
        double angle = 180.0f / (mColumns * 2) * Math.PI / 180;
        float centerY = getMeasuredHeight() / 2.0f - getMeasuredHeight() / mRows / 2;
        for(int i = 0;i < mRows;i++){
            for(int j = 0;j < mColumns;j++){
                /**
                 * x        :   centerX - cos α * R
                 * y        :   centerY - cos α * R
                 * z        :   centerY - sin α * R
                 * rotateX  :   90 - α
                 */
                double realAngle = 2 * j * angle + angle + startAngle * Math.PI / 180;
                coordZ[i * mColumns + j] = (float) (centerY - Math.sin(realAngle) * mRadius);
            }
        }
        return coordZ;
    }

    /**
     * bitmap围绕x轴旋转多少度
     * @return
     */
    private float[] getRotateX(){
        float[] rotateX = new float[mRows * mColumns];
        double angle = 180.0f / (mRows * 2) * Math.PI / 180;
        for(int i = 0;i < mRows;i++){
            for(int j = 0;j < mColumns;j++){
                /**
                 * x        :   centerX - cos α * R
                 * y        :   centerY - cos α * R
                 * z        :   centerY - sin α * R
                 * rotateX  :   90 - α
                 */
                double realAngle = 2 * i * angle + angle;
                rotateX[i * mColumns + j] = (float) (90 - (realAngle / (Math.PI / 180)));
            }
        }
        return rotateX;
    }

    /**
     * bitmap围绕y轴旋转多少度
     * @param startAngle
     * @return
     */
    private float[] getRotateY(double startAngle){
        float[] rotateY = new float[mRows * mColumns];
        double angle = 180.0f / (mColumns * 2) * Math.PI / 180;

        for(int i = 0;i < mRows;i++){
            for(int j = 0;j < mColumns;j++){
                /**
                 * x        :   centerX - cos α * R
                 * y        :   centerY - cos α * R
                 * z        :   centerY - sin α * R
                 * rotateX  :   90 - α
                 */
                double realAngle = 2 * j * angle + angle + startAngle * Math.PI / 180;
                rotateY[i * mColumns + j] = (float) ((realAngle / (Math.PI / 180)) - 90);
            }
        }
        return rotateY;
    }

    /**
     *
     * @param canvas            画布
     * @param bitmap            位图
     * @param coordX            x坐标
     * @param coordY            y坐标
     * @param rotateX           绕x轴旋转度数
     * @param rotateY           绕y轴旋转度数
     * @param coordZ1           相对与y轴上的bitmap的z轴上的偏移
     * @param coordZ2           相对与x轴上的bitmap的z轴上的偏移
     * @param oldCoordX         原始x坐标
     * @param oldCoordY         原始y坐标
     * @param transformRatio    滑动比率
     * @param mPaint            画笔
     */
    private void drawCanvas(Canvas canvas, Bitmap bitmap, float coordX, float coordY, float rotateX, float rotateY, float coordZ1, float coordZ2, float oldCoordX, float oldCoordY, float transformRatio, Paint mPaint){
        mMatrix.reset();
        canvas.save();
        mCamera.save();

        mCamera.translate(0.0f, 0.0f, coordZ2 * transformRatio);
        mCamera.rotateY(rotateY * transformRatio);
        mCamera.translate(0.0f, 0.0f, coordZ1 * transformRatio);
        mCamera.rotateX(rotateX * transformRatio);
        mCamera.getMatrix(mMatrix);
        mMatrix.preScale(1 - (1 - scaleX) * transformRatio, 1 - (1 - scaleY) * transformRatio);
        mMatrix.preTranslate(-(bitmap.getWidth() / 2), -(bitmap.getHeight() / 2));
        mMatrix.postTranslate(+(bitmap.getWidth() / 2), +(bitmap.getHeight() / 2));
        mMatrix.postTranslate(oldCoordX + (coordX - oldCoordX) * transformRatio, oldCoordY + (coordY - oldCoordY) * transformRatio);
        canvas.drawBitmap(bitmap, mMatrix, mPaint);

        mCamera.restore();
        canvas.restore();
    }

    private void drawCurPageArea(Canvas canvas, Bitmap bitmap) {
        /**
         * 将图片切成mColumn*mColumn份
         */
        if (isSplitCurImage) {
            isSplitCurImage = false;
            curImagePieces = split(bitmap, mRows, mColumns);

            if(!isSetCoord){
                initData();
                isSetCoord = true;
            }
        }

        float transformRatio = Math.abs(deltaDistance) / (mWidth / 4);
        if(transformRatio <= 1.0f){
            float[] coordZ2 = getCoordZ2(0);
            float[] rotateY = getRotateY(0);
            float[] coordX = getCoordX(0);
            isCurrentFirst = false;

            for(int i = 0;i < mRows * mColumns;i++){
                mPaint.setColorFilter(null);

                drawCanvas(canvas, curImagePieces.get(i).bitmap,
                        coordX[i], coordY[i],
                        rotateX[i], rotateY[i],
                        coordZ1[i], coordZ2[i],
                        oldCoordX[i], oldCoordY[i],
                        transformRatio, mPaint);
            }
        }else{
            transformRatio = (Math.abs(deltaDistance) - mWidth / 4) / (mWidth / 4);
            /**
             * 小于0说明是从右往左滑动,所以当前page从0到-180度转动,所以先画第一列最后画最后一列（后面档住前面的）
             */
            int feg = 180;
            int sI = mRows * mColumns - 1;
            int eI = 0;
            int avg = -1;
            if(deltaDistance < 0){
                feg = -180;
                sI = 0;
                eI = mRows * mColumns;
                avg = 1;
            }

            if(transformRatio * -180 < -90){
                isCurrentFirst = true;
            }else{
                isCurrentFirst = false;
            }
            if(transformRatio <= 1.0f){
                float[] coordZ2 = getCoordZ2(transformRatio * feg);
                float[] rotateY = getRotateY(transformRatio * feg);
                float[] coordX = getCoordX(transformRatio * feg);

                for(int i = sI;deltaDistance < 0 && i < eI || deltaDistance > 0 && i >= eI;i = i + avg){
                    if(coordZ2[i] > mRadius){
                        mPaint.setColorFilter(mColorMatrixFilter);
                    }else{
                        mPaint.setColorFilter(null);
                    }
                    drawCanvas(canvas, curImagePieces.get(i).bitmap,
                            coordX[i], coordY[i],
                            rotateX[i], rotateY[i],
                            coordZ1[i], coordZ2[i],
                            oldCoordX[i], oldCoordY[i],
                            1.0f, mPaint);
                }
            }else{
                float[] coordZ2 = getCoordZ2(feg);
                float[] rotateY = getRotateY(feg);
                float[] coordX = getCoordX(feg);

                for(int i = 0;i < mRows * mColumns;i++){
                    mPaint.setColorFilter(mColorMatrixFilter);

                    drawCanvas(canvas, curImagePieces.get(i).bitmap,
                            coordX[i], coordY[i],
                            rotateX[i], rotateY[i],
                            coordZ1[i], coordZ2[i],
                            oldCoordX[i], oldCoordY[i],
                            1.0f, mPaint);
                }
            }
        }

    }

    private void drawNextPageArea(Canvas canvas, Bitmap bitmap) {
        /**
         * 将图片切成mColumn*mColumn份
         */
        if (isSplitNextImage) {
            isSplitNextImage = false;
            nextImagePieces = split(bitmap, mRows, mColumns);

            if(!isSetCoord){
                initData();
                isSetCoord = true;
            }
        }

        float transformRatio = (Math.abs(deltaDistance) - mWidth / 4) / (mWidth / 4);
        if(transformRatio < 0){
            return;
        }
        if(transformRatio <= 1.0f){
            /**
             * 小于0说明是从右往左滑动,所以当前page从180到0度转动,所以先画最后一列最后画第一列（前面档住后面的）
             */
            int feg = -180;
            int sI = 0;
            int eI = mRows * mColumns;
            int avg = 1;
            if(deltaDistance < 0){
                feg = 180;
                sI = mRows * mColumns - 1;
                eI = 0;
                avg = -1;
            }

            float[] coordZ2 = getCoordZ2(feg * (1 - transformRatio));
            float[] rotateY = getRotateY(feg * (1 - transformRatio));
            float[] coordX = getCoordX(feg * (1 - transformRatio));
            for(int i = sI;deltaDistance < 0 && i >= eI || deltaDistance > 0 && i < eI;i = i + avg){
                if(coordZ2[i] > mRadius){
                    mPaint.setColorFilter(mColorMatrixFilter);
                }else{
                    mPaint.setColorFilter(null);
                }
                drawCanvas(canvas, nextImagePieces.get(i).bitmap,
                        coordX[i], coordY[i],
                        rotateX[i], rotateY[i],
                        coordZ1[i], coordZ2[i],
                        oldCoordX[i], oldCoordY[i],
                        1.0f, mPaint);
            }
        }else{
            transformRatio = Math.abs((Math.abs(deltaDistance) - mWidth / 2) / (mWidth / 4));
            if(transformRatio > 1.0f){
                transformRatio = 1.0f;
            }
            float[] coordZ2 = getCoordZ2(0);
            float[] rotateY = getRotateY(0);
            float[] coordX = getCoordX(0);
            for(int i = 0;i < mRows * mColumns;i++){
                mPaint.setColorFilter(null);
                drawCanvas(canvas, nextImagePieces.get(i).bitmap,
                        coordX[i], coordY[i],
                        rotateX[i], rotateY[i],
                        coordZ1[i], coordZ2[i],
                        oldCoordX[i], oldCoordY[i],
                        1.0f - transformRatio, mPaint);
            }
        }
    }

    public void setBitmaps(Bitmap bm1, Bitmap bm2) {
        mCurPageBitmap = bm1;
        mNextPageBitmap = bm2;
        isSplitCurImage = true;
        isSplitNextImage = true;
        this.postInvalidate();
    }

    public boolean doTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            mTouch.x = (float) Math.ceil(event.getX());
            mTouch.y = (float) Math.ceil(event.getY());
            this.postInvalidate();
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mFirstTouch.x = (float) Math.ceil(event.getX());
            mFirstTouch.y = (float) Math.ceil(event.getY());
            mTouch.x = (float) Math.ceil(event.getX());
            mTouch.y = (float) Math.ceil(event.getY());
            deltaDistance = 0.0f;
            this.postInvalidate();
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (canDragOver()) {
                startAnimation(2000);
            }else{
                startAnimation(1500);
            }
            this.postInvalidate();
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFFAAAAAA);
        deltaDistance = mTouch.x - mFirstTouch.x;
        if(deltaDistance < 0.0f){
            if(deltaDistance > -2.0f) {
                deltaDistance = 0.0f;
            }
        }else{
            if(deltaDistance < 2.0f) {
                deltaDistance = 0.0f;
            }
        }
        if(!isCurrentFirst){
            if(mNextPageBitmap != null){
                //后半球
                drawNextPageArea(canvas, mNextPageBitmap);
            }
            //前半球
            drawCurPageArea(canvas, mCurPageBitmap);
        }else{
            //前半球
            drawCurPageArea(canvas, mCurPageBitmap);
            if(mNextPageBitmap != null){
                //后半球
                drawNextPageArea(canvas, mNextPageBitmap);
            }
        }
    }

    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            float x = mScroller.getCurrX();
            float y = mScroller.getCurrY();
            mTouch.x = x;
            mTouch.y = y;
            postInvalidate();
        }
    }

    private void startAnimation(int delayMillis) {
        int dx;
        float tmpDeltaDistance = mTouch.x - mFirstTouch.x;
        if (tmpDeltaDistance < 0) {
            if(Math.abs(tmpDeltaDistance) > mWidth / 2){
                dx = -(mWidth + 10);
            }else{
                dx = (int) Math.abs(tmpDeltaDistance);
            }
        } else {
            if(tmpDeltaDistance > mWidth / 2){
                dx = (mWidth + 10);
            }else{
                dx = (int) -tmpDeltaDistance;
            }
        }
        mScroller.startScroll((int) mTouch.x, 0, dx, 0, delayMillis);
    }

    public void abortAnimation() {
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
    }

    public boolean isAnimationRunning(){
        return !mScroller.isFinished();
    }

    public boolean canDragOver() {
        if (Math.abs(deltaDistance) > mWidth / 2)
            return true;
        return false;
    }
}
