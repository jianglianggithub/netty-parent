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

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Description of algorithm for PageRun/PoolSubpage allocation from PoolChunk
 *
 * Notation: The following terms are important to understand the code
 * > page  - a page is the smallest unit of memory chunk that can be allocated
 * > chunk - a chunk is a collection of pages
 * > in this code chunkSize = 2^{maxOrder} * pageSize
 *
 * To begin we allocate a byte array of size = chunkSize
 * Whenever a ByteBuf of given size needs to be created we search for the first position
 * in the byte array that has enough empty space to accommodate the requested size and
 * return a (long) handle that encodes this offset information, (this memory segment is then
 * marked as reserved so it is always used by exactly one ByteBuf and no more)
 *
 * For simplicity all sizes are normalized according to PoolArena#normalizeCapacity method
 * This ensures that when we request for memory segments of size >= pageSize the normalizedCapacity
 * equals the next nearest power of 2
 *
 * To search for the first offset in chunk that has at least requested size available we construct a
 * complete balanced binary tree and store it in an array (just like heaps) - memoryMap
 *
 * The tree looks like this (the size of each node being mentioned in the parenthesis)
 *
 * depth=0        1 node (chunkSize)
 * depth=1        2 nodes (chunkSize/2)
 * ..
 * ..
 * depth=d        2^d nodes (chunkSize/2^d)
 * ..
 * depth=maxOrder 2^maxOrder nodes (chunkSize/2^{maxOrder} = pageSize)
 *
 * depth=maxOrder is the last level and the leafs consist of pages
 *
 * With this tree available searching in chunkArray translates like this:
 * To allocate a memory segment of size chunkSize/2^k we search for the first node (from left) at height k
 * which is unused
 *
 * Algorithm:
 * ----------
 * Encode the tree in memoryMap with the notation
 *   memoryMap[id] = x => in the subtree rooted at id, the first node that is free to be allocated
 *   is at depth x (counted from depth=0) i.e., at depths [depth_of_id, x), there is no node that is free
 *
 *  As we allocate & free nodes, we update values stored in memoryMap so that the property is maintained
 *
 * Initialization -
 *   In the beginning we construct the memoryMap array by storing the depth of a node at each node
 *     i.e., memoryMap[id] = depth_of_id
 *
 * Observations:
 * -------------
 * 1) memoryMap[id] = depth_of_id  => it is free / unallocated
 * 2) memoryMap[id] > depth_of_id  => at least one of its child nodes is allocated, so we cannot allocate it, but
 *                                    some of its children can still be allocated based on their availability
 * 3) memoryMap[id] = maxOrder + 1 => the node is fully allocated & thus none of its children can be allocated, it
 *                                    is thus marked as unusable
 *
 * Algorithm: [allocateNode(d) => we want to find the first node (from left) at height h that can be allocated]
 * ----------
 * 1) start at root (i.e., depth = 0 or id = 1)
 * 2) if memoryMap[1] > d => cannot be allocated from this chunk
 * 3) if left node value <= h; we can allocate from left subtree so move to left and repeat until found
 * 4) else try in right subtree
 *
 * Algorithm: [allocateRun(size)]
 * ----------
 * 1) Compute d = log_2(chunkSize/size)
 * 2) Return allocateNode(d)
 *
 * Algorithm: [allocateSubpage(size)]
 * ----------
 * 1) use allocateNode(maxOrder) to find an empty (i.e., unused) leaf (i.e., page)
 * 2) use this handle to construct the PoolSubpage object or if it already exists just call init(normCapacity)
 *    note that this PoolSubpage object is added to subpagesPool in the PoolArena when we init() it
 *
 * Note:
 * -----
 * In the implementation for improving cache coherence,
 * we store 2 pieces of information depth_of_id and x as two byte values in memoryMap and depthMap respectively
 *
 * memoryMap[id]= depth_of_id  is defined above
 * depthMap[id]= x  indicates that the first node which is free to be allocated is at depth x (from root)
 */
final class PoolChunk<T> implements PoolChunkMetric {

    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;

    final PoolArena<T> arena;
    final T memory;
    final boolean unpooled;
    final int offset;


    //  memoryMap 存放每个Node 的使用记录 一旦被使用 该Node 值= 【maxOrder=H】 + 1
    // 然后 parentNode 的value 等于 left  rigth Node 的最小value 然后通过一定的算法 可以得出 哪些Node 可用 不可用
    private final byte[] memoryMap;
    private final byte[] depthMap;

    //  每个 叶子节点  的大小为  8k = 8192 byte  当需要分配的缓冲区 的 容量 <  这个值的 时候 就会把 对应的 pageNode 分成 一个subPages
    // 而 subpages 也是分为tiny  samll   前者 放的是 16  496 byte 大小的 node,后者 放的是 512 - 4096 byte  大小的node
    private final PoolSubpage<T>[] subpages;// 存放所有 页子节点 2048个
    /** Used to determine if the requested capacity is equal to or greater than pageSize. */
    private final int subpageOverflowMask;
    private final int pageSize;
    private final int pageShifts;
    private final int maxOrder;
    private final int chunkSize;
    private final int log2ChunkSize;//chunksize = 1 << log2ChunkSize
    private final int maxSubpageAllocs;
    /** Used to mark memory as unusable */
    private final byte unusable;

    // Use as cache for ByteBuffer created from the memory. These are just duplicates and so are only a container
    // around the memory itself. These are often needed for operations within the Pooled*ByteBuf and so
    // may produce extra GC, which can be greatly reduced by caching the duplicates.
    //
    // This may be null if the PoolChunk is unpooled as pooling the ByteBuffer instances does not make any sense here.
    private final Deque<ByteBuffer> cachedNioBuffers;

    private int freeBytes;

    PoolChunkList<T> parent;
    PoolChunk<T> prev;
    PoolChunk<T> next;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize, int offset) {
        unpooled = false;
        this.arena = arena; // 所属的 arena
        this.memory = memory; // byteBuff  或者 bute[]  代表了  这整个 chunk 的缓冲区
        this.pageSize = pageSize; // 每个叶子节点的 大小 8192
        this.pageShifts = pageShifts;  // 13     1<<13 = 2048  代表有这个多个叶子节点
        this.maxOrder = maxOrder; // 树的高度 默认11
        this.chunkSize = chunkSize; //  16mb
        this.offset = offset;
        unusable = (byte) (maxOrder + 1);  // 代表该 高度下的 所有节点 为不可用 在通过算法计算 选择 节点的时候 有帮助

        log2ChunkSize = log2(chunkSize);  // 暂时 不看 不知道有什么用

        subpageOverflowMask = ~(pageSize - 1); // - 8192  用来判断 创建缓冲区的大小是否 需要 分成 subpage

        freeBytes = chunkSize; // 可分配的字节数 剩余容量

        assert maxOrder < 30 : "maxOrder should be < 30, but is: " + maxOrder;
        maxSubpageAllocs = 1 << maxOrder;// 2048 个节点  用来表示2048个叶子节点 所用到的 数组长度

        // Generate the memory map.
        memoryMap = new byte[maxSubpageAllocs << 1];
        depthMap = new byte[memoryMap.length];
        int memoryMapIndex = 1;
        for (int d = 0; d <= maxOrder; ++ d) { // move down the tree one level at a time
            int depth = 1 << d;
            for (int p = 0; p < depth; ++ p) {
                // in each level traverse left to right and set value to the depth of subtree
                memoryMap[memoryMapIndex] = (byte) d;
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex ++;
            }
        }

        subpages = newSubpageArray(maxSubpageAllocs); // 创建 subpage[] 2048个叶子节点 就有可能出现 2048个 subpage
        cachedNioBuffers = new ArrayDeque<ByteBuffer>(8);// 缓存 nio的 byteBuff 的个数 默认是8个 一个2048byte？
    }

    /** Creates a special chunk that is not pooled. */
    PoolChunk(PoolArena<T> arena, T memory, int size, int offset) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        this.offset = offset;
        memoryMap = null;
        depthMap = null;
        subpages = null;
        subpageOverflowMask = 0;
        pageSize = 0;
        pageShifts = 0;
        maxOrder = 0;
        unusable = (byte) (maxOrder + 1);
        chunkSize = size;
        log2ChunkSize = log2(chunkSize);
        maxSubpageAllocs = 0;
        cachedNioBuffers = null;
    }

    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpageArray(int size) {
        return new PoolSubpage[size];
    }

    @Override
    public int usage() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }
        return usage(freeBytes);
    }

    private int usage(int freeBytes) {
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize); // 得到 对应 chunk快 中 剩余的容量占比
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;// 得到 已经使用 字节的在chunk中的占比
    }

    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        final long handle;
        // >= pageSize  normal 这个地方是一个算法 相当于 8192 = n个11  -8192 = 高位1 n个1 前面是1 n个1位置上是0
        if ((normCapacity & subpageOverflowMask) != 0) {
            handle =  allocateRun(normCapacity);
        } else {// 如果是subPage < 8192 的
            handle = allocateSubpage(normCapacity);
        }
        //  -1 代表分配失败
        // 如果 当前容量大小 大于 subPage 那么返回的是 当前 分配的 缓冲区在 chunk 中的 idx
        // 如果小于 subpage 那么 返回的handle 其实是 返回的 当前容量 位于 chunk中的第几个 subpage【idx】
        // 和 当前 创建的缓冲区位于第几个 缓存快
        if (handle < 0) {
            return false;
        }
        ByteBuffer nioBuffer = cachedNioBuffers != null ? cachedNioBuffers.pollLast() : null;
        initBuf(buf, nioBuffer, handle, reqCapacity);
        return true;
    }

    /**
     * Update method used by allocate
     * This is triggered only when a successor is allocated and all its predecessors
     * need to update their state
     * The minimal depth at which subtree rooted at id has some free space
     *
     * @param id id
     */
    private void updateParentsAlloc(int id) {
        while (id > 1) {
            int parentId = id >>> 1;
            /**
             *  如果当前是 右节点那么获取左节点 反之 获得对应的value 将最小值设置为 parentID 的value
             */
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            byte val = val1 < val2 ? val1 : val2;
            setValue(parentId, val);
            id = parentId;
        }
    }

    public static void main(String[] args) {
        System.out.println(3 ^ 1);
    }
    /**
     * Update method used by free
     * This needs to handle the special case when both children are completely free
     * in which case parent be directly allocated on request of size = child-size * 2
     *
     * @param id id
     */
    private void updateParentsFree(int id) {
        int logChild = depth(id) + 1;
        while (id > 1) {
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            logChild -= 1; // in first iteration equals log, subsequently reduce 1 from logChild as we traverse up

            if (val1 == logChild && val2 == logChild) {
                setValue(parentId, (byte) (logChild - 1));
            } else {
                byte val = val1 < val2 ? val1 : val2;
                setValue(parentId, val);
            }

            id = parentId;
        }
    }

    /**
     * Algorithm to allocate an index in memoryMap when we query for a free node
     * at depth d
     *
     * @param d depth
     * @return index in memoryMap
     */
    private int allocateNode(int d) {

        /*
            第一个node 的 val 能代表 当前chunk 对应的 完全二叉树中 第一个能被【完整分配】 node 所在的层数
         */


        int id = 1;
        int initial = - (1 << d); // 得到对应 h 的 all nodes count 那么 id < math.abs(initial) = true
        byte val = value(id);// val = 最近的可用层
        if (val > d) { // val = “距离当前节点最近层的“ 有可用节点的 h   如果 要分配的  d 也就是 h 比 val < 那么可以直接得出
                        // 不满足条件了  因为在这层 之上 都不可能有完整的 一个节点及其子节点 都是 bitmap[id] = dept[id] 了
            return -1;
        }


        /*
            如果 val < d  代表 还没有 找到对应 d 所在层的node 的id  当相等的时候 代表 已经找到了 对应 层可用node 的id
            因为一个node 如果 可用的话 那么 一定是 当前node 所在的高度= bitmap[id] 的
            如果不满足那么这个node 一定不能完全被分配

            如果相等 代表 当前节点 所在的 h 是需要分配 容量所需要的h 也就是【d】

            这里会有多种情况
                就是 对应node 没有被使用 但是呢 对应 node 的value 不是要分配的 d 对应的 h

                在就是 node 被使用了 而且 对应 node val > d  这种情况不可能出现。。至于为什么 想想就知道了

                如果 相等的情况下 id < 对应层数的节点总数 代表 没到对应真正的层 因为
                可额 这个节点的子节点被使用了 那么这个节点的val = d 了 但是并没有到 真正的层数

                这个里面有一个简单的算法就是  1 << h  其实 等于 当前层的个数 也等于 当前层的 startIndex 所以 小于这个index 自然不是这个层的

         */
        while (val < d || (id & initial) == 0) { // id & initial == 1 << d for all ids at depth d, for < d it is 0
            id <<= 1;//拿到 左1 子节点值
            val = value(id);

            /*
                如果 对应 id node 的左节点 val > d  代表 这个节点 已经 被使用过了 无法 在完整被分配了 那么获得 右节点的id
                拿到右节点的 val  那么 下一个循环 就可以判断 val == d ？ 如果相等 代表 找到了 需要的 h 对应的层 的 node
             */
            if (val > d) {
                id ^= 1;
                val = value(id);
            }
        }
        // 得到可用的节点了
        byte value = value(id);
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
                value, id & initial, d);

        // 将 bitMap[id] 设置为不可用  并且parentId 递归设置成 最近的可用节点 高度
        setValue(id, unusable); // mark as unusable
        updateParentsAlloc(id);
        return id;
    }



    /**
     * Allocate a run of pages (>=1)
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    private long allocateRun(int normCapacity) {
        // 如果  需要分配的 缓冲区大小 》= 8192 bytes
        // 计算出 对应容量在 树的高度
        int i = log2(normCapacity);

        /**
         *  这个算法思路是 叶子节点 容量 是 2的多少次方  然后用 对应容量计算出是2的多少次方 设想 如果我每比你大 1 那么 不就是 需要分配的容量 = 你的几倍么？
         *   那么 叶子节点的 父节点= pagesize *2 以此类推 那么 i - pageShifts = 多出来的倍数 每多一倍树的高度就减少1就可求出对应容量在树的高度了
         *
         *    数学计算公式就是   x1  x2 是2的指数倍的情况下（假设 x1=8192 x2 > x1）  那么就是 x1= 2^n 次方 x2 = 2^(n+y)
         *    那么 x1 与 x2 差值 = 2^(n+y-n) 那么 (i - pageShifts) = y y >0代表 x2是x1 的 2^y 呗
         */
        int d = maxOrder - (i - pageShifts);// 1 << pageShifts = pageSize
        int id = allocateNode(d);// 找到对应高度 可用的节点
        if (id < 0) {
            return id;
        }
        freeBytes -= runLength(id);// 通过id 计算出 使用的  chunk中的长度大小  id 越小使用的  字节就越多  比11高1层就是 8192*2  继续高就继续*2 累乘
        return id;
    }
    /**
     * Create / initialize a new PoolSubpage of normCapacity
     * Any PoolSubpage created / initialized here is added to subpage pool in the PoolArena that owns this PoolChunk
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    private long allocateSubpage(int normCapacity) {
        /**
         *   如果分配的是subPage 那么找到 对应分配的容量 在 tiny[] 或者small[] 中的head节点
         *   然后将 chunk 对应的 page 分成对应 node 容量区间大小的 subpage 追加到链表之中
         *
         *   每个 arena 会分配对应初始化的 headNode 在这儿就做出了体现 反之 null指针Excption
         */
        PoolSubpage<T> head = arena.findSubpagePoolHead(normCapacity);
        int d = maxOrder;
        synchronized (head) {
            // 在chunk中拿到一个 可用的 8192 的 page 页节点 也就是 高度= 11那层 并且设置为占用状态
            int id = allocateNode(d);
            if (id < 0) {
                return id;
            }

            final PoolSubpage<T>[] subpages = this.subpages;
            final int pageSize = this.pageSize;

            freeBytes -= pageSize;

            /* 得到对应 页子节点 的 subpage index 然后找到对应 于 2048 个 node 第几个 然后 在subpages中 初始化并分配 */

            int subpageIdx = subpageIdx(id);//  %  计算 拿到的 index 对应在 最后一层 的offset
            PoolSubpage<T> subpage = subpages[subpageIdx];
            if (subpage == null) {
                subpage = new PoolSubpage<T>(head, this, id, runOffset(id), pageSize, normCapacity);
                subpages[subpageIdx] = subpage;// 一个chunk 有 2048个 subpage 将占用了的subpage初始化到chunk下所有的subpage 对应index中
            } else {
                // 。这儿 我知道了。 释放的时候不会 将对应idx = null 掉。而是缓存 这儿将对应复用的subpage 从新初始化
                subpage.init(head, normCapacity);
            }

            /*
            *   在对应的subpage 中分配对应的内存块 标记为使用 如果没有可用的 item count 了 那么将自己从 链表中删除
            *   如果分配到 将可用item --
            * */
            return subpage.allocate();
        }
    }

    /**
     * Free a subpage or a run of pages
     * When a subpage is freed from PoolSubpage, it might be added back to subpage pool of the owning PoolArena
     * If the subpage pool in PoolArena has at least one other PoolSubpage of given elemSize, we can
     * completely free the owning Page so it is available for subsequent allocations
     *
     * @param handle handle to free
     */
    void free(long handle, ByteBuffer nioBuffer) {
        // 通过handle 得到使用 的subpage 在chunk中的 idx
        int memoryMapIdx = memoryMapIdx(handle);
        // 使用的是subpage第几快内存
        int bitmapIdx = bitmapIdx(handle);

        if (bitmapIdx != 0) { // 条件成立 代表使用了 subpage 因为 > 8192 返回的是 idx >>> 32 后肯定是  = 0 的
            PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
            assert subpage != null && subpage.doNotDestroy;

            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
            // This is need as we may add it back and so alter the linked-list structure.
            PoolSubpage<T> head = arena.findSubpagePoolHead(subpage.elemSize);
            // 重入锁。之前已经拿到了Arena的lock
            synchronized (head) {
                // 如果返回flase 代表该subpage 的容量 使用率=0 那么将其回收  下面就是 回收对应的容量 将chunk的menoryMap deptMap进行++
                if (subpage.free(head, bitmapIdx & 0x3FFFFFFF)) {
                    return;                     // bitmapIdx & 0x3FFFFFFF 直接拿到 当前内存快是 subpage中的第几个
                }
            }
        }
        //回收对应内存快到chunk

        freeBytes += runLength(memoryMapIdx);
        setValue(memoryMapIdx, depth(memoryMapIdx));
        updateParentsFree(memoryMapIdx);

        // 缓存相关。
        if (nioBuffer != null && cachedNioBuffers != null &&
                cachedNioBuffers.size() < PooledByteBufAllocator.DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK) {
            cachedNioBuffers.offer(nioBuffer);
        }
    }

    void initBuf(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity) {
        // 得到当前容量subpage节点所处在bitmap中的idx
        int memoryMapIdx = memoryMapIdx(handle);

        //  如果 当前 使用的缓冲区大小 >= 8192 页的大小 那么 就不存在 bitmap【bitmap index 代表了 当前subPage 位于 对应的分区大小 的 page的offset偏移量 因为index* elesize就可以得到偏移量】

        /*
             >>> 32 得到 处于 subpage 中的 第几个。 如果 = 0 那么就是 >= subpage 的。否则就是 < subpage 容量的缓冲区

         */
        int bitmapIdx = bitmapIdx(handle);
        // 如果是非 subpage 那么
        if (bitmapIdx == 0) {
            // subpage
            byte val = value(memoryMapIdx);
            assert val == unusable : String.valueOf(val);
            // 一样的、计算offset
            buf.init(this, nioBuffer, handle, runOffset(memoryMapIdx) + offset,
                    reqCapacity, runLength(memoryMapIdx), arena.parent.threadCache());
        } else {
            // >= 8192
            initBufWithSubpage(buf, nioBuffer, handle, bitmapIdx, reqCapacity);
        }
    }

    void initBufWithSubpage(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity) {
        initBufWithSubpage(buf, nioBuffer, handle, bitmapIdx(handle), reqCapacity);
    }

    private void initBufWithSubpage(PooledByteBuf<T> buf, ByteBuffer nioBuffer,
                                    long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;

        // 得到对应handle 的 在chunk中的 subpage idx 然后 %
        int memoryMapIdx = memoryMapIdx(handle);
        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];

        assert subpage.doNotDestroy;
        assert reqCapacity <= subpage.elemSize;

        buf.init(
            this, nioBuffer, handle,
            // bitmapIdx 第31 位=1  0x3FFFFFFF 011111111 这样除掉 handle 最高位的1
                // runOffset(memoryMapIdx) 得到 suapge对应在chunk中的offset
                // (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize + offset 得到 当前 分配subpage 中 的第几个内存块 * 每个subpage 中小 碎片的size
                // 得到了 subpage中的 offset  那么 就得出了 在chunk中全局的offset
            runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize + offset,
                reqCapacity, subpage.elemSize, arena.parent.threadCache()
        );
    }

    private byte value(int id) {
        return memoryMap[id];
    }

    private void setValue(int id, byte val) {
        memoryMap[id] = val;
    }

    private byte depth(int id) {
        return depthMap[id];
    }

    private static int log2(int val) {
        // compute the (0-based, with lsb = 0) position of highest set bit i.e, log2
        return INTEGER_SIZE_MINUS_ONE - Integer.numberOfLeadingZeros(val);
    }

    /**
     *  得到 对应 id 对应 高度 对应 node 层的 支持的 最大 内存快的长度
     *  depth = d， 2^d nodes， nodeSize = chunkSize/(2^d)    ps: 要记住一件事情 h itemsize = 把整个chunk 平均分配 到item的长度
     *   这个就是这个公式的缩写。。  2 ^ log2ChunkSize / 2 ^ d  =  2^(n-d) 么？ 佛了。数学全忘了
     * @param id
     * @return
     */
    private int runLength(int id) {
        // represents the size in #bytes supported by node 'id' in the tree
        return 1 << log2ChunkSize - depth(id);
    }

    /* 得到对应id 在对应层的 容量 offset  = index * itemSize */
    private int runOffset(int id) {
        // represents the 0-based offset in #bytes from start of the byte-array chunk

        int shift = id ^ 1 << depth(id);// 1  << dept(id) = 当前层的 items count  这个 shift 值 = 当前 node 在 对应层中的 offset
        return shift * runLength(id);
    }
    // = %
    private int subpageIdx(int memoryMapIdx) {
        return memoryMapIdx ^ maxSubpageAllocs; // remove highest set bit, to get offset
    }

    private static int memoryMapIdx(long handle) {
        return (int) handle;
    }

    private static int bitmapIdx(long handle) {
        return (int) (handle >>> Integer.SIZE);
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    @Override
    public int freeBytes() {
        synchronized (arena) {
            return freeBytes;
        }
    }

    @Override
    public String toString() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }

        return new StringBuilder()
                .append("Chunk(")
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append(": ")
                .append(usage(freeBytes))
                .append("%, ")
                .append(chunkSize - freeBytes)
                .append('/')
                .append(chunkSize)
                .append(')')
                .toString();
    }

    void destroy() {
        arena.destroyChunk(this);
    }
}
