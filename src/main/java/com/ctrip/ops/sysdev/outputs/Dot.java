package com.ctrip.ops.sysdev.outputs;

import java.util.Map;

/**
 * Created by xukunfeng on 16/3/28.
 */
public class Dot extends  BaseOutput{


    public Dot(Map config) {
        super(config);
    }

    @Override
    protected void prepare() {

    }

    @Override
    protected void emit(Map event) {
        System.out.print(".");

    }
}
