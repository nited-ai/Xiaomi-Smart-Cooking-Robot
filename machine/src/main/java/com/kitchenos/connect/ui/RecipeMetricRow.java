package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.Paint;
import android.view.View;
import android.widget.*;
import com.kitchenos.common.Ui;

/** Four inline facts: use spare width as spacing, keeping long numbers readable. */
final class RecipeMetricRow extends LinearLayout {
    private final Paint measure=new Paint();
    RecipeMetricRow(Context context){super(context);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int available=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
        if(MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED&&available>0&&getChildCount()>0){
            int preferredGap=Ui.dp(getContext(),8),minimumGap=Ui.dp(getContext(),3),total=0;float chosenSize=14;
            for(float size=14;size>=12;size-=.5f){
                total=0;chosenSize=size;
                for(int i=0;i<getChildCount();i++){
                    LinearLayout cell=(LinearLayout)getChildAt(i);TextView label=(TextView)cell.getChildAt(1);measure.set(label.getPaint());measure.setTextSize(size*getResources().getDisplayMetrics().scaledDensity);
                    View icon=cell.getChildAt(0);LayoutParams textSize=(LayoutParams)label.getLayoutParams();
                    total+=icon.getLayoutParams().width+textSize.leftMargin+(int)Math.ceil(measure.measureText(label.getText().toString()));
                }
                if(total+preferredGap*(getChildCount()-1)<=available)break;
            }
            for(int i=0;i<getChildCount();i++){LinearLayout cell=(LinearLayout)getChildAt(i);((TextView)cell.getChildAt(1)).setTextSize(chosenSize);((LayoutParams)cell.getLayoutParams()).leftMargin=i==0?0:minimumGap;}
        }
        super.onMeasure(widthSpec,heightSpec);
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);
        int count=getChildCount(),total=0;
        for(int i=0;i<count;i++)total+=getChildAt(i).getMeasuredWidth();
        int spare=Math.max(0,getWidth()-getPaddingLeft()-getPaddingRight()-total),used=0;
        for(int i=0;i<count;i++){
            View cell=getChildAt(i);
            // Rounding the cumulative gap keeps both outer edges aligned and gaps within 1 px.
            int x=getPaddingLeft()+used+(count>1?Math.round((float)spare*i/(count-1)):0);
            cell.layout(x,cell.getTop(),x+cell.getMeasuredWidth(),cell.getBottom());used+=cell.getMeasuredWidth();
        }
    }
}
