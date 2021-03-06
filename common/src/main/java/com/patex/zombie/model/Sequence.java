package com.patex.zombie.model;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class Sequence {

    private Long id;

    private String name;

    private Instant updated;

    private List<SequenceBook> books = new ArrayList<>();

}