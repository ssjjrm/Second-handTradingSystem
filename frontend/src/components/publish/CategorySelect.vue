<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getCategoryTree } from '@/api/category'

/**
 * 二级分类联动选择。
 *
 * 为什么必须显式选：闲鱼靠"智能识别属性"自动归类（他们在用 AI 识别图片），
 * 我们不做识别，所以**发布时必须选**，而且**只能选二级分类** ——
 * 后端也校验（`parent_id IS NOT NULL`），挂在二级分类上买家才筛得出来。
 */
const props = defineProps({
  /** v-model:parentId / v-model:categoryId */
  parentId: { type: [Number, String], default: '' },
  categoryId: { type: [Number, String], default: '' }
})
const emit = defineEmits(['update:parentId', 'update:categoryId'])

const tree = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    tree.value = await getCategoryTree()
  } catch {
    tree.value = []
  } finally {
    loading.value = false
  }
}
onMounted(load)

const children = computed(() => {
  const parent = tree.value.find((c) => String(c.id) === String(props.parentId))
  return parent?.children || []
})

function onParentChange(e) {
  const id = e.target.value
  emit('update:parentId', id)
  // 换了父分类，原来的二级分类就不成立了，必须清空
  emit('update:categoryId', '')
}

function onChildChange(e) {
  emit('update:categoryId', e.target.value)
}

// 编辑场景：如果只给了 categoryId，反推出父分类（本阶段没做编辑，但留着不吃亏）
watch(
  () => props.categoryId,
  (cid) => {
    if (!cid || props.parentId) return
    const parent = tree.value.find((p) => (p.children || []).some((c) => String(c.id) === String(cid)))
    if (parent) emit('update:parentId', String(parent.id))
  }
)
</script>

<template>
  <div class="category-select">
    <select class="select" :value="parentId" :disabled="loading" @change="onParentChange">
      <option value="">选择一级分类</option>
      <option v-for="p in tree" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
    </select>

    <select
      class="select"
      :value="categoryId"
      :disabled="!parentId || !children.length"
      @change="onChildChange"
    >
      <option value="">
        {{ parentId ? (children.length ? '选择二级分类' : '该分类下暂无子类') : '先选一级分类' }}
      </option>
      <option v-for="c in children" :key="c.id" :value="String(c.id)">{{ c.name }}</option>
    </select>
  </div>
</template>

<style scoped>
.category-select {
  display: flex;
  gap: var(--gap-2);
}
.select {
  height: 38px;
  min-width: 160px;
  padding: 0 10px;
  background: #fff;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  outline: none;
  transition: border-color var(--dur-fast), box-shadow var(--dur-fast);
}
.select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(255, 209, 0, 0.18);
}
.select:disabled {
  color: var(--color-text-muted);
  background: #fafbfc;
}
</style>
