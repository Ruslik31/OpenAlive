package org.aliveclean;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import java.util.function.BiConsumer;

/** Loaded with Xiaomi's RecyclerView, not the Flyme editor's library version. */
public final class XiaomiListAdapter extends RecyclerView.Adapter<XiaomiListAdapter.Holder> {
    private final Context context;
    private final int layout,count;
    private final BiConsumer<View,Integer> binder;
    public XiaomiListAdapter(Context context,int layout,int count,BiConsumer<View,Integer> binder){this.context=context;this.layout=layout;this.count=count;this.binder=binder;}
    public static final class Holder extends RecyclerView.ViewHolder {public Holder(View view){super(view);}}
    @Override public Holder onCreateViewHolder(ViewGroup parent,int type){return new Holder(LayoutInflater.from(context).inflate(layout,parent,false));}
    @Override public void onBindViewHolder(Holder holder,int position){binder.accept(holder.itemView,position);}
    @Override public int getItemCount(){return count;}
}
