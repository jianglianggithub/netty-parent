/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

final class PoolSubpage<T> implements PoolSubpageMetric {

    final PoolChunk<T> chunk;
    private final int memoryMapIdx;// 当前 subPage 对应 在chunk中的 index

    // 这个值 有点复杂   比如 树 高度 = h  那么 当前 subpage 在 最后一层的 offset = 1 的位置 那么 这个值 就 = offset * 对应层的 内存快长度
    //  就是 offset =1  这个值 =  8192  以此内推  = 对应subpage 的 startIndex 在 对应层下
    private final int runOffset;
    private final int pageSize;
    private final long[] bitmap;

    PoolSubpage<T> prev;
    PoolSubpage<T> next;

    boolean doNotDestroy;
    int elemSize;// item Size
    private int maxNumElems; // 最大可用的小分区个数

    // bitMap 默认是 8个long 一个long 表示64个 那么就能满足 当16 为item size 的时候  maxItemCount = 512个来纪录 每个item的使用量
    // subpage 最大分区= 8192/16 =512个 所以 默认是 64*8
    // 这里的 length 代表有几个long 来表示 subpage  中 每个小分区 是否被使用
    private int bitmapLength;

    private int nextAvail;// 下一个可用的node index
    private int numAvail; // 可用item count

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /** Special constructor that creates a linked list head */
    PoolSubpage(int pageSize) {
        chunk = null;
        memoryMapIdx = -1;
        runOffset = -1;
        elemSize = -1;
        this.pageSize = pageSize;
        bitmap = null;
    }

    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.runOffset = runOffset;
        this.pageSize = pageSize;
        bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64 【16=最小容量 /64 = 一个long的位数】
        init(head, elemSize);// 初始化节点所需要的 bitmap 个数 和总itemss个数等等
    }

    void init(PoolSubpage<T> head, int elemSize) {
        doNotDestroy = true;
        this.elemSize = elemSize;
        if (elemSize != 0) {
            maxNumElems = numAvail = pageSize / elemSize;
            nextAvail = 0;
            bitmapLength = maxNumElems >>> 6;// 查看maxNumElemets 是 64 的多少倍 / 64 每 64 占用一个 long 一个long 可表示 64个item使用信息

            // 这里求余数。如果 / 64 后有余数 那么 +1 。。。。netty这他妈也要优化。我佛了
            if ((maxNumElems & 63) != 0) {
                bitmapLength ++;
            }

            for (int i = 0; i < bitmapLength; i ++) {
                bitmap[i] = 0;
            }
        }
        addToPool(head);
    }

    /**
     * Returns the bitmap index of the subpage allocation.
     */
    long allocate() {
        if (elemSize == 0) {
            return toHandle(0);
        }
        // 可用个数为0
        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        //  得到当前subpage 下一个可用的 node 的Index
        final int bitmapIdx = getNextAvail();
        int q = bitmapIdx >>> 6;// 拿到整除数
        int r = bitmapIdx & 63;// 拿到余数  整除数 = index 第几个 bitmap[] 中的第几个long 然后余数表示第几个
        assert (bitmap[q] >>> r & 1) == 0;
        bitmap[q] |= 1L << r;// 将 64位的long 对应的 subpage中的小分区 =1 代表已使用  如果bitmapIdx = 0 那么 就是 第 0个long 的第 0位
                              //  第64个 就是 第一个long 第0个 跟那个rocketmq 那个说实话 挺像  满64 = 0 1 << 0 又是1 。完美。

        // -- 之后可用节点=0 那么 删除本身 因为没有可用的节点了。
        if (-- numAvail == 0) {
            removeFromPool();// 如果当没有可用 的 items 了 那么将自己 在tany small 对应区间节点中删除。
        }

        return toHandle(bitmapIdx);
    }

    /**
     * @return {@code true} if this subpage is in use.
     *         {@code false} if this subpage is not used by its chunk and thus it's OK to be released.
     */
    boolean free(PoolSubpage<T> head, int bitmapIdx) {
        if (elemSize == 0) {
            return true;
        }
        int q = bitmapIdx >>> 6;// 得到第几个 bitLong
        int r = bitmapIdx & 63;// 处于 第几个bitLong 中的 index
        assert (bitmap[q] >>> r & 1) != 0;
        bitmap[q] ^= 1L << r;// 将 1 ^ 1 至空 为0

        setNextAvail(bitmapIdx);
        // 之前可用为0 之前将 this 在 对应的 PageArena 对应 tiny small [] 链表中移除了 那么加回去
        if (numAvail ++ == 0) {
            addToPool(head);
            return true;
        }

        if (numAvail != maxNumElems) {// 不等 代表 里面至少还有一个小分区正在被使用 无法回收  【只要有subpage中有一个小分区在使用就不能回收】
            return true;
        } else {
            // 如果 该subpage全部释放了 但是如果 该 容量对应的 分区 tany 或者 small 中 的链表只有这一个节点 那么 还是保留 没必要回收 【太多了不回收旧是浪费内存】
            // Subpage not in use (numAvail == maxNumElems)
            if (prev == next) {
                // Do not remove if this subpage is the only one left in the pool.
                return true;
            }

            // Remove this subpage from the pool if there are other subpages left in the pool.
            // 其他情况 直接 返回fasle 回收这个 page 然后将 subpage从链表中删除
            doNotDestroy = false;
            removeFromPool();
            return false;
        }
    }
    // 后续追加的节点 会放在 headNode.nextNode下 因为只有对应容量的节点分配不出内存后才会创建新的节点
    private void addToPool(PoolSubpage<T> head) {
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    private void setNextAvail(int bitmapIdx) {
        nextAvail = bitmapIdx;
    }

    private int getNextAvail() {
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    private int findNextAvail() {
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i ++) {
            long bits = bitmap[i];
            // 取反如果等于0 代表 long 每个位上都是1
            if (~bits != 0) {
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    private int findNextAvail0(int i, long bits) {
        final int maxNumElems = this.maxNumElems;
        final int baseVal = i << 6;

        for (int j = 0; j < 64; j ++) {
            // 条件成立代表 long 的 最低位 就是 0 ， 1的最高位=0 所以 bits 高位全变0
            if ((bits & 1) == 0) {
                //。默认 6个long 单是不是每个 subpage 都能用满6个long 小于 maxNumElems 那么代表可用直接返回
                int val = baseVal | j;
                if (val < maxNumElems) {
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1;
        }
        return -1;
    }
    // 这个值 是纪录 一个整体 信息 高出int位 纪录 当前 使用的内存块 位于 subpage 中的 idx【第几个】 int位 纪录当前内存块 使用的是 在 chunk中的第几个node
    private long toHandle(int bitmapIdx) {
        //  0x4000000000000000L  = 0 1 + 62个0

        return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    }

    @Override
    public String toString() {
        final boolean doNotDestroy;
        final int maxNumElems;
        final int numAvail;
        final int elemSize;
        if (chunk == null) {
            // This is the head so there is no need to synchronize at all as these never change.
            doNotDestroy = true;
            maxNumElems = 0;
            numAvail = 0;
            elemSize = -1;
        } else {
            synchronized (chunk.arena) {
                if (!this.doNotDestroy) {
                    doNotDestroy = false;
                    // Not used for creating the String.
                    maxNumElems = numAvail = elemSize = -1;
                } else {
                    doNotDestroy = true;
                    maxNumElems = this.maxNumElems;
                    numAvail = this.numAvail;
                    elemSize = this.elemSize;
                }
            }
        }

        if (!doNotDestroy) {
            return "(" + memoryMapIdx + ": not in use)";
        }

        return "(" + memoryMapIdx + ": " + (maxNumElems - numAvail) + '/' + maxNumElems +
                ", offset: " + runOffset + ", length: " + pageSize + ", elemSize: " + elemSize + ')';
    }

    @Override
    public int maxNumElements() {
        if (chunk == null) {
            // It's the head.
            return 0;
        }

        synchronized (chunk.arena) {
            return maxNumElems;
        }
    }

    @Override
    public int numAvailable() {
        if (chunk == null) {
            // It's the head.
            return 0;
        }

        synchronized (chunk.arena) {
            return numAvail;
        }
    }

    @Override
    public int elementSize() {
        if (chunk == null) {
            // It's the head.
            return -1;
        }

        synchronized (chunk.arena) {
            return elemSize;
        }
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    void destroy() {
        if (chunk != null) {
            chunk.destroy();
        }
    }
}
