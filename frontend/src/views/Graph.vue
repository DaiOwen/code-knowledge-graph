<template>
  <div class="graph-page">
    <!-- Toolbar -->
    <div class="graph-toolbar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索节点..."
        class="search-input"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select v-model="selectedNodeType" placeholder="节点类型" class="node-type-select" @change="loadGraph">
        <el-option label="全部" value="" />
        <el-option label="类" value="class" />
        <el-option label="方法" value="method" />
        <el-option label="字段" value="field" />
      </el-select>

      <el-button-group>
        <el-button :icon="ZoomIn" @click="zoomIn" />
        <el-button :icon="ZoomOut" @click="zoomOut" />
        <el-button :icon="Refresh" @click="resetZoom" />
        <el-button :icon="Download" @click="exportGraph" />
      </el-button-group>

      <div class="legend">
        <span class="legend-item">
          <span class="legend-dot class-dot"></span> 类
        </span>
        <span class="legend-item">
          <span class="legend-dot method-dot"></span> 方法
        </span>
        <span class="legend-item">
          <span class="legend-dot field-dot"></span> 字段
        </span>
      </div>
    </div>

    <!-- Graph Container -->
    <div class="graph-container" ref="graphContainer">
      <div v-if="loading" class="loading-overlay">
        <el-icon class="is-loading" :size="48"><Loading /></el-icon>
        <p>加载图谱数据中...</p>
      </div>

      <svg ref="svgElement" class="graph-svg">
        <defs>
          <!-- Arrow marker for relationships -->
          <marker
            id="arrowhead"
            markerWidth="10"
            markerHeight="7"
            refX="25"
            refY="3.5"
            orient="auto"
          >
            <polygon points="0 0, 10 3.5, 0 7" fill="#999" />
          </marker>

          <!-- Glow filter for highlighted nodes -->
          <filter id="glow">
            <feGaussianBlur stdDeviation="3" result="coloredBlur" />
            <feMerge>
              <feMergeNode in="coloredBlur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <!-- Relationships (edges) -->
        <g class="relationships-layer">
          <line
            v-for="edge in visibleEdges"
            :key="edge.id"
            :x1="getNodeX(edge.sourceId!)"
            :y1="getNodeY(edge.sourceId!)"
            :x2="getNodeX(edge.targetId!)"
            :y2="getNodeY(edge.targetId!)"
            :class="['edge', edge.type.toLowerCase(), { highlighted: isEdgeHighlighted(edge) }]"
            marker-end="url(#arrowhead)"
          />
        </g>

        <!-- Nodes -->
        <g class="nodes-layer">
          <g
            v-for="node in visibleNodes"
            :key="node.id"
            :transform="`translate(${node.x}, ${node.y})`"
            :class="['node-group', { selected: selectedNode?.id === node.id, highlighted: isNodeHighlighted(node) }]"
            @click="selectNode(node)"
            @mousedown="startDrag($event, node)"
          >
            <!-- Node circle -->
            <circle
              :r="getNodeRadius(node)"
              :class="['node-circle', node.labels[0]?.toLowerCase()]"
            />
            <!-- Node label -->
            <text
              class="node-label"
              dy="-15"
              text-anchor="middle"
            >
              {{ truncateText(node.properties.name || node.id, 15) }}
            </text>
          </g>
        </g>
      </svg>

      <!-- Empty state -->
      <el-empty v-if="!loading && nodes.length === 0" description="暂无图谱数据，请先解析项目" />
    </div>

    <!-- Node Detail Panel -->
    <el-drawer
      v-model="showDetailPanel"
      title="节点详情"
      direction="rtl"
      size="400px"
    >
      <div v-if="selectedNode" class="node-detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="名称">
            {{ selectedNode.properties.name }}
          </el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :type="getNodeTypeTag(selectedNode)">
              {{ selectedNode.labels[0] }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="selectedNode.properties.filePath" label="文件">
            <el-link type="primary" @click="goToFile(selectedNode)">
              {{ selectedNode.properties.filePath }}
            </el-link>
          </el-descriptions-item>
          <el-descriptions-item v-if="selectedNode.properties.startLine" label="行号">
            {{ selectedNode.properties.startLine }}
          </el-descriptions-item>
          <el-descriptions-item v-if="selectedNode.properties.signature" label="签名">
            <code class="signature-code">{{ selectedNode.properties.signature }}</code>
          </el-descriptions-item>
        </el-descriptions>

        <!-- Relationships -->
        <div class="relationships-section">
          <h4>调用关系</h4>
          <el-tabs>
            <el-tab-pane label="调用者">
              <el-empty v-if="incomingEdges.length === 0" description="无调用者" :image-size="60" />
              <div v-else class="relation-list">
                <div
                  v-for="edge in incomingEdges"
                  :key="edge.id"
                  class="relation-item"
                  @click="focusNode(edge.sourceId!)"
                >
                  <el-icon><ArrowLeft /></el-icon>
                  <span>{{ getNodeById(edge.sourceId!)?.properties.name }}</span>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="被调用">
              <el-empty v-if="outgoingEdges.length === 0" description="无被调用方法" :image-size="60" />
              <div v-else class="relation-list">
                <div
                  v-for="edge in outgoingEdges"
                  :key="edge.id"
                  class="relation-item"
                  @click="focusNode(edge.targetId!)"
                >
                  <span>{{ getNodeById(edge.targetId!)?.properties.name }}</span>
                  <el-icon><ArrowRight /></el-icon>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <!-- Actions -->
        <div class="node-actions">
          <el-button type="primary" @click="showImpactAnalysis">
            影响分析
          </el-button>
          <el-button @click="askAboutNode">
            提问
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- Impact Analysis Dialog -->
    <el-dialog v-model="showImpactDialog" title="影响分析" width="600px">
      <div v-if="impactLoading" class="impact-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <p>分析中...</p>
      </div>
      <div v-else-if="impactData" class="impact-result">
        <el-alert
          v-if="impactData.nodes.length === 0"
          type="info"
          title="该方法没有下游依赖"
          :closable="false"
        />
        <template v-else>
          <p>修改此方法可能影响 <strong>{{ impactData.nodes.length }}</strong> 个节点：</p>
          <el-tree
            :data="impactTree"
            :props="{ label: 'name', children: 'children' }"
            default-expand-all
          >
            <template #default="{ data }">
              <span class="impact-node">
                <el-tag :type="data.type === 'method' ? 'success' : 'info'" size="small">
                  {{ data.type }}
                </el-tag>
                <span>{{ data.name }}</span>
                <span v-if="data.filePath" class="impact-file">{{ data.filePath }}</span>
              </span>
            </template>
          </el-tree>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search, ZoomIn, ZoomOut, Refresh, Download,
  ArrowLeft, ArrowRight, Loading
} from '@element-plus/icons-vue'
import * as d3 from 'd3'
import type { GraphNode, GraphRelationship, GraphData } from '@/api/graph'
import { getProjectGraph, getImpactAnalysis } from '@/api/graph'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))

// Refs
const graphContainer = ref<HTMLElement>()
const svgElement = ref<SVGSVGElement>()

// State
const loading = ref(false)
const nodes = ref<GraphNode[]>([])
const edges = ref<GraphRelationship[]>([])
const searchQuery = ref('')
const selectedNodeType = ref('')
const selectedNode = ref<GraphNode | null>(null)
const showDetailPanel = ref(false)
const showImpactDialog = ref(false)
const impactLoading = ref(false)
const impactData = ref<GraphData | null>(null)
const highlightedNodes = ref<Set<string>>(new Set())

// Zoom and pan state
const transform = ref({ x: 0, y: 0, k: 1 })
const isDragging = ref(false)
const dragNode = ref<GraphNode | null>(null)

// Computed
const visibleNodes = computed(() => {
  let filtered = nodes.value

  if (selectedNodeType.value) {
    filtered = filtered.filter(n => n.labels.includes(selectedNodeType.value))
  }

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    filtered = filtered.filter(n =>
      n.properties.name?.toLowerCase().includes(query)
    )
  }

  return filtered.map(node => {
    // Ensure node has position
    if (node.x === undefined) {
      node.x = Math.random() * 800 + 100
    }
    if (node.y === undefined) {
      node.y = Math.random() * 600 + 100
    }
    return node
  })
})

const visibleEdges = computed(() => {
  const nodeIds = new Set(visibleNodes.value.map(n => n.id))
  return edges.value.filter(e => nodeIds.has(e.sourceId!) && nodeIds.has(e.targetId!))
})

const incomingEdges = computed(() => {
  if (!selectedNode.value) return []
  return edges.value.filter(e => e.targetId === selectedNode.value!.id)
})

const outgoingEdges = computed(() => {
  if (!selectedNode.value) return []
  return edges.value.filter(e => e.sourceId === selectedNode.value!.id)
})

const impactTree = computed(() => {
  if (!impactData.value) return []
  return buildImpactTree(impactData.value)
})

// Methods
async function loadGraph() {
  loading.value = true
  try {
    const response = await getProjectGraph(projectId.value, {
      nodeType: selectedNodeType.value || undefined,
      limit: 200
    })
    if (response.success) {
      nodes.value = response.data.nodes
      edges.value = response.data.relationships

      // Build node map and set edge source/target
      new Map(nodes.value.map(n => [n.id, n]))

      edges.value = edges.value.map(edge => ({
        ...edge,
        sourceId: edge.startNodeId,
        targetId: edge.endNodeId
      }))

      // Initialize D3 force layout
      initForceLayout()
    }
  } catch (error: any) {
    ElMessage.error('加载图谱失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function initForceLayout() {
  const width = graphContainer.value?.clientWidth || 1000
  const height = graphContainer.value?.clientHeight || 700

  const simulation = d3.forceSimulation(visibleNodes.value as any)
    .force('link', d3.forceLink(edges.value as any)
      .id((d: any) => d.id)
      .distance(150)
      .strength(0.5)
    )
    .force('charge', d3.forceManyBody().strength(-300))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collision', d3.forceCollide().radius(40))

  simulation.on('tick', () => {
    // Update positions
    visibleNodes.value.forEach(node => {
      node.x = Math.max(50, Math.min(width - 50, node.x || 0))
      node.y = Math.max(50, Math.min(height - 50, node.y || 0))
    })
  })

  // Run simulation for a bit
  simulation.tick(100)
}

function handleSearch() {
  if (searchQuery.value) {
    const found = nodes.value.find(n =>
      n.properties.name?.toLowerCase().includes(searchQuery.value.toLowerCase())
    )
    if (found) {
      focusNode(found.id)
    }
  }
}

function selectNode(node: GraphNode) {
  selectedNode.value = node
  showDetailPanel.value = true
}

function focusNode(nodeId: string) {
  const node = nodes.value.find(n => n.id === nodeId)
  if (node) {
    selectedNode.value = node
    highlightedNodes.value.clear()
    highlightedNodes.value.add(nodeId)

    // Highlight connected nodes
    edges.value.forEach(edge => {
      if (edge.sourceId === nodeId) {
        highlightedNodes.value.add(edge.targetId!)
      }
      if (edge.targetId === nodeId) {
        highlightedNodes.value.add(edge.sourceId!)
      }
    })

    // Center view on node
    if (graphContainer.value && node.x !== undefined && node.y !== undefined) {
      const width = graphContainer.value.clientWidth
      const height = graphContainer.value.clientHeight
      transform.value.x = width / 2 - node.x * transform.value.k
      transform.value.y = height / 2 - node.y * transform.value.k
    }
  }
}

function getNodeById(id: string) {
  return nodes.value.find(n => n.id === id)
}

function getNodeX(id: string) {
  const node = getNodeById(id)
  return node?.x || 0
}

function getNodeY(id: string) {
  const node = getNodeById(id)
  return node?.y || 0
}

function getNodeRadius(node: GraphNode) {
  if (node.labels.includes('class')) return 25
  if (node.labels.includes('method')) return 18
  return 14
}

function truncateText(text: string, maxLength: number) {
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

function getNodeTypeTag(node: GraphNode) {
  if (node.labels.includes('class')) return 'primary'
  if (node.labels.includes('method')) return 'success'
  return 'info'
}

function isNodeHighlighted(node: GraphNode) {
  return highlightedNodes.value.has(node.id)
}

function isEdgeHighlighted(edge: GraphRelationship) {
  return highlightedNodes.value.has(edge.sourceId!) || highlightedNodes.value.has(edge.targetId!)
}

function startDrag(event: MouseEvent, node: GraphNode) {
  event.preventDefault()
  isDragging.value = true
  dragNode.value = node

  const handleMove = (e: MouseEvent) => {
    if (dragNode.value) {
      dragNode.value.x = (e.clientX - transform.value.x) / transform.value.k
      dragNode.value.y = (e.clientY - transform.value.y) / transform.value.k
    }
  }

  const handleUp = () => {
    isDragging.value = false
    dragNode.value = null
    document.removeEventListener('mousemove', handleMove)
    document.removeEventListener('mouseup', handleUp)
  }

  document.addEventListener('mousemove', handleMove)
  document.addEventListener('mouseup', handleUp)
}

function zoomIn() {
  transform.value.k = Math.min(3, transform.value.k * 1.2)
}

function zoomOut() {
  transform.value.k = Math.max(0.3, transform.value.k / 1.2)
}

function resetZoom() {
  transform.value = { x: 0, y: 0, k: 1 }
}

function exportGraph() {
  const svg = svgElement.value
  if (!svg) return

  const serializer = new XMLSerializer()
  const svgString = serializer.serializeToString(svg)
  const blob = new Blob([svgString], { type: 'image/svg+xml' })
  const url = URL.createObjectURL(blob)

  const a = document.createElement('a')
  a.href = url
  a.download = `graph-${projectId.value}.svg`
  a.click()
  URL.revokeObjectURL(url)

  ElMessage.success('图谱已导出')
}

function goToFile(node: GraphNode) {
  if (node.properties.filePath && node.properties.startLine) {
    // Navigate to file viewer with line highlighted
    router.push({
      path: `/project/${projectId.value}/files`,
      query: { path: node.properties.filePath, line: node.properties.startLine }
    })
  }
}

async function showImpactAnalysis() {
  if (!selectedNode.value) return

  showImpactDialog.value = true
  impactLoading.value = true
  impactData.value = null

  try {
    const response = await getImpactAnalysis(
      projectId.value,
      selectedNode.value.properties.name || '',
      3
    )
    if (response.success) {
      impactData.value = response.data
    }
  } catch (error: any) {
    ElMessage.error('影响分析失败: ' + (error.message || '未知错误'))
  } finally {
    impactLoading.value = false
  }
}

function buildImpactTree(data: GraphData): any[] {
  const nodeMap = new Map(data.nodes.map(n => [n.id, n]))
  const tree: any[] = []
  const added = new Set<string>()

  data.relationships.forEach(edge => {
    const target = nodeMap.get(edge.endNodeId)
    if (target && !added.has(target.id)) {
      added.add(target.id)
      tree.push({
        id: target.id,
        name: target.properties.name || target.id,
        type: target.labels[0],
        filePath: target.properties.filePath,
        children: []
      })
    }
  })

  return tree
}

function askAboutNode() {
  if (!selectedNode.value) return
  // Navigate to QA page with context
  const nodeName = selectedNode.value.properties.name
  router.push({
    path: `/project/${projectId.value}/qa`,
    query: { question: `谁调用了 ${nodeName} 方法？` }
  })
}

// Lifecycle
onMounted(() => {
  loadGraph()
})
</script>

<style scoped lang="scss">
.graph-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.graph-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: white;
  border-bottom: 1px solid #e4e7ed;

  .search-input {
    width: 250px;
  }

  .node-type-select {
    width: 120px;
  }

  .legend {
    margin-left: auto;
    display: flex;
    gap: 16px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: #606266;
    }

    .legend-dot {
      width: 12px;
      height: 12px;
      border-radius: 50%;

      &.class-dot { background: #409eff; }
      &.method-dot { background: #67c23a; }
      &.field-dot { background: #e6a23c; }
    }
  }
}

.graph-container {
  flex: 1;
  position: relative;
  overflow: hidden;

  .loading-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.8);
    z-index: 10;
  }
}

.graph-svg {
  width: 100%;
  height: 100%;
  background: white;
}

.edge {
  stroke: #dcdfe6;
  stroke-width: 1.5px;
  fill: none;

  &.calls {
    stroke: #67c23a;
  }

  &.extends, &.implements {
    stroke: #409eff;
    stroke-dasharray: 5, 5;
  }

  &.highlighted {
    stroke: #409eff;
    stroke-width: 2px;
  }
}

.node-group {
  cursor: pointer;
  transition: transform 0.2s;

  &:hover {
    .node-circle {
      filter: url(#glow);
    }
  }

  &.selected {
    .node-circle {
      stroke: #409eff;
      stroke-width: 3px;
    }
  }

  &.highlighted {
    .node-circle {
      fill: #ecf5ff;
    }
  }
}

.node-circle {
  fill: white;
  stroke: #dcdfe6;
  stroke-width: 2px;
  transition: all 0.2s;

  &.class {
    fill: #ecf5ff;
    stroke: #409eff;
  }

  &.method {
    fill: #f0f9eb;
    stroke: #67c23a;
  }

  &.field {
    fill: #fdf6ec;
    stroke: #e6a23c;
  }
}

.node-label {
  font-size: 12px;
  fill: #303133;
  pointer-events: none;
}

.node-detail {
  .signature-code {
    font-family: 'Fira Code', monospace;
    font-size: 12px;
    background: #f5f7fa;
    padding: 4px 8px;
    border-radius: 4px;
    display: block;
    word-break: break-all;
  }

  .relationships-section {
    margin-top: 20px;

    h4 {
      margin-bottom: 12px;
      color: #303133;
    }

    .relation-list {
      max-height: 200px;
      overflow-y: auto;
    }

    .relation-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      border-radius: 4px;
      cursor: pointer;
      transition: background 0.2s;

      &:hover {
        background: #f5f7fa;
      }
    }
  }

  .node-actions {
    margin-top: 20px;
    display: flex;
    gap: 12px;
  }
}

.impact-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px;
}

.impact-result {
  .impact-node {
    display: flex;
    align-items: center;
    gap: 8px;

    .impact-file {
      margin-left: auto;
      color: #909399;
      font-size: 12px;
    }
  }
}
</style>
