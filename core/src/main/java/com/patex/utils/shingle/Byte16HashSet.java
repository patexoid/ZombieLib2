package com.patex.utils.shingle;

import java.util.Arrays;


class Byte16HashSet {

    private static final int MAXIMUM_CAPACITY = 1 << 30;

    private transient Node[] table;

    Byte16HashSet(int initialCapacity) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                    initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        table = new Node[tableSizeFor(initialCapacity)];
    }

    private static int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return n < 0 ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    static int getHashCode(byte[] key) {
        return Arrays.hashCode(key);
    }

    private int index(int hashCode) {
        int i = hashCode ^ (hashCode >>> 16);
        return (table.length - 1) & i;
    }

    boolean contains(byte[] key) {
        int hashCode = getHashCode(key);
        int index = index(hashCode);
        Node node = table[index];
        if (node != null) {
            do {
                if (node.hashCode == hashCode && node.isEqualsArray(key))
                    return true;
            } while ((node = node.getNext()) != null);
        }
        return false;
    }

    public void add(byte[] key) {
        int hashCode = getHashCode(key);
        int index = index(hashCode);
        Node node = table[index];
        if (node == null)
            table[index] = new Node(key);
        else {
            do {
                if (node.hashCode == hashCode && node.isEqualsArray(key))
                    return;
            } while ((node = node.getNext()) != null);
            table[index] = new NodeNext(key, table[index]);
        }
    }

    static class Node {
        final int hashCode;
        final byte b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13, b14, b15;

        Node(byte[] key) {
            b0 = key[0];
            b1 = key[1];
            b2 = key[2];
            b3 = key[3];
            b4 = key[4];
            b5 = key[5];
            b6 = key[6];
            b7 = key[7];
            b8 = key[8];
            b9 = key[9];
            b10 = key[10];
            b11 = key[11];
            b12 = key[12];
            b13 = key[13];
            b14 = key[14];
            b15 = key[15];

            this.hashCode = getHashCode(key);
        }

        boolean isEqualsArray(byte[] key) {

            if (b0 != key[0]) return false;
            if (b1 != key[1]) return false;
            if (b2 != key[2]) return false;
            if (b3 != key[3]) return false;
            if (b4 != key[4]) return false;
            if (b5 != key[5]) return false;
            if (b6 != key[6]) return false;
            if (b7 != key[7]) return false;
            if (b8 != key[8]) return false;
            if (b9 != key[9]) return false;
            if (b10 != key[10]) return false;
            if (b11 != key[11]) return false;
            if (b12 != key[12]) return false;
            if (b13 != key[13]) return false;
            if (b14 != key[14]) return false;
            //noinspection RedundantIfStatement
            if (b15 != key[15]) return false;

            return true;
        }

        public Node getNext() {
            return null;
        }

    }

    static class NodeNext extends Node {
        Node next;

        NodeNext(byte[] key, Node next) {
            super(key);
            this.next = next;
        }

        @Override
        public Node getNext() {
            return next;
        }
    }


}
