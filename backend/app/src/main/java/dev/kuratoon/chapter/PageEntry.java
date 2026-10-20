package dev.kuratoon.chapter;

/**
 * One page in {@code chapter.pages}: file name inside the chapter folder and pixel dimensions, so
 * the reader can reserve layout space. JSON keys {@code f}/{@code w}/{@code h} per spec 01.
 */
public record PageEntry(String f, int w, int h) {}
