package com.healthsystem.vo;
import java.util.List;
public record PageVO<T>(int page,int size,long total,List<T> items) {}
