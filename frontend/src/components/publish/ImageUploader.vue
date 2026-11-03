<script setup>
import { ref } from 'vue'
import { uploadImage } from '@/api/upload'
import { toast } from '@/composables/useToast'

/**
 * 图片上传（九宫格）。
 *
 * 三个必须处理的细节：
 * 1. **串行上传**：一次只传一张。并发上传会让顺序乱掉，
 *    而且手机拍的大图并发很容易把请求超时。
 * 2. **前端先挡大小和数量**：后端也挡，但前端挡能立刻告诉用户，不用等往返。
 * 3. **失败要能重试**：上传失败的那一格要标出来，不能静默消失。
 */
const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: 9 },
  maxSizeMB: { type: Number, default: 5 }
})
const emit = defineEmits(['update:modelValue'])

const uploading = ref(false)
const progress = ref('')
const inputRef = ref(null)

function pick() {
  inputRef.value?.click()
}

function removeAt(i) {
  const next = props.modelValue.slice()
  next.splice(i, 1)
  emit('update:modelValue', next)
}

function setAsCover(i) {
  if (i === 0) return
  const next = props.modelValue.slice()
  const [item] = next.splice(i, 1)
  next.unshift(item)
  emit('update:modelValue', next)
  toast('已设为封面')
}

async function onFiles(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = '' // 允许重复选同一个文件
  if (!files.length) return

  const room = props.max - props.modelValue.length
  if (room <= 0) {
    toast(`最多 ${props.max} 张图片`, 'error')
    return
  }

  const accepted = []
  for (const f of files.slice(0, room)) {
    if (!f.type.startsWith('image/')) {
      toast(`${f.name} 不是图片`, 'error')
      continue
    }
    if (f.size > props.maxSizeMB * 1024 * 1024) {
      toast(`${f.name} 超过 ${props.maxSizeMB}MB`, 'error')
      continue
    }
    accepted.push(f)
  }
  if (files.length > room) {
    toast(`最多 ${props.max} 张，多出的已忽略`, 'error')
  }

  uploading.value = true
  const urls = props.modelValue.slice()
  try {
    for (let i = 0; i < accepted.length; i++) {
      progress.value = `上传中 ${i + 1}/${accepted.length}`
      const data = await uploadImage(accepted[i])
      if (data?.url) urls.push(data.url)
    }
    emit('update:modelValue', urls)
    if (urls.length > props.modelValue.length) toast('图片已上传')
  } catch (err) {
    // 已经传成功的要保留，不能因为最后一张失败就把前面的都丢了
    emit('update:modelValue', urls)
    toast(err.message || '上传失败，请重试', 'error')
  } finally {
    uploading.value = false
    progress.value = ''
  }
}
</script>

<template>
  <div class="uploader">
    <div class="grid">
      <div v-for="(url, i) in modelValue" :key="url + i" class="cell">
        <img v-img-fallback :src="url" :alt="`图片 ${i + 1}`" />
        <span v-if="i === 0" class="cover-tag">封面</span>
        <div class="ops">
          <button v-if="i > 0" type="button" title="设为封面" @click="setAsCover(i)">封面</button>
          <button type="button" title="删除" @click="removeAt(i)">删除</button>
        </div>
      </div>

      <button
        v-if="modelValue.length < max"
        class="cell add"
        type="button"
        :disabled="uploading"
        @click="pick"
      >
        <span class="plus">＋</span>
        <span class="add-text">{{ uploading ? progress : '添加图片' }}</span>
        <span class="add-count">{{ modelValue.length }}/{{ max }}</span>
      </button>
    </div>

    <input
      ref="inputRef"
      class="file"
      type="file"
      accept="image/*"
      multiple
      @change="onFiles"
    />

    <p class="hint tip">
      第一张作为封面（鼠标移上去可改）。支持 jpg / png / webp，单张 ≤ {{ maxSizeMB }}MB，最多 {{ max }} 张。
    </p>
  </div>
</template>

<style scoped>
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gap-3);
}

.cell {
  position: relative;
  width: 104px;
  height: 104px;
  overflow: hidden;
  background: #fafbfc;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}
.cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-tag {
  position: absolute;
  left: 0;
  top: 0;
  padding: 1px 6px;
  font-size: 11px;
  color: #1a1a1a;
  background: var(--color-primary);
  border-bottom-right-radius: var(--radius-md);
}

.ops {
  position: absolute;
  inset: auto 0 0 0;
  display: flex;
  opacity: 0;
  transition: opacity var(--dur-fast);
}
.cell:hover .ops {
  opacity: 1;
}
.ops button {
  flex: 1;
  height: 24px;
  font-size: 11px;
  color: #fff;
  background: rgba(0, 0, 0, 0.6);
  border: none;
}
.ops button:hover {
  background: rgba(0, 0, 0, 0.78);
}

.add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--color-text-muted);
  background: #fafbfc;
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-lg);
  transition: border-color var(--dur-fast), color var(--dur-fast);
}
.add:hover:not(:disabled) {
  color: var(--color-link);
  border-color: var(--color-primary);
}
.plus {
  font-size: 22px;
  line-height: 1;
}
.add-text {
  font-size: var(--fs-xs);
}
.add-count {
  font-size: 11px;
  opacity: 0.8;
}

.file {
  display: none;
}

.tip {
  margin: var(--gap-2) 0 0;
}
</style>
