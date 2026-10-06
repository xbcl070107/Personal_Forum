/**
 * 时间格式化。
 *
 * 后端返回的 createdAt 是 ISO 格式的 "2026-09-29T14:35:14"，
 * 直接显示给用户太长也不好看。这里统一成 "2026-09-29 14:35"。
 *
 * 为什么不用 dayjs / moment：就这一个需求，装个库不值。真需要
 * 「3 分钟前」「昨天」这种相对时间，再考虑装。
 */
export function formatTime(str) {
  if (!str) return ''
  const d = new Date(str)
  if (Number.isNaN(d.getTime())) return str
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 帖子审核状态的文字和颜色。
 *
 * 跟后端 Post 实体里那三个常量一一对应：
 * 0 = 待审核，1 = 已通过，2 = 已驳回
 *
 * 抽成一处是因为「我的帖子」「审核列表」两个页面都要用，
 * 各写一份的话，哪天加第四个状态就会漏。
 */
export const STATUS_MAP = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已驳回', type: 'danger' },
}

export function statusOf(status) {
  return STATUS_MAP[status] || { text: '未知', type: 'info' }
}

/**
 * 作者身份的「长相」——名字和颜色。
 *
 * 【为什么需要它】
 * 后端的列表 / 详情接口只返回 userId 这个数字，没有作者名
 * （见 PostService.listApproved —— 返回的是裸 Post 实体）。
 * 而上一条帖子到底是谁发的，是列表里最该看见的信息之一：
 * 之前详情页直接写死了一个「冒险者」，等于所有帖子都是同一个人发的，
 * 这个信息就废了。
 *
 * 【为什么不去改后端】
 * 要真名就得给帖子接口做 VO、连 user 表查用户名 —— 那是后端的活，
 * 而且这个项目当前的学习重点是后端分层，前端不该为了显示一个名字
 * 就把接口形状改掉。所以这里退一步：由 userId 稳定地推出一张
 * 「同一作者永远同一个名字和颜色」的身份牌。
 *
 * 【为什么是确定的】
 * 同一个 userId 永远算出同一组结果，所以列表和详情里
 * 同一个作者长得一样，翻页也不会变脸。换个库、id 变了，
 * 名字跟着变 —— 这一点它比不上真名，是明知的取舍。
 */
const AUTHOR_NAMES = [
  '灰羽',
  '林间旅人',
  '铁匠的女儿',
  '北境信使',
  '抄书人',
  '夜航船',
  '钟表匠',
  '拾荒者',
  '山雀',
  '提灯人',
]

/** 和顶栏、评论区共用的一份色板，同一个人在哪都一个色 */
export const AVATAR_PALETTE = ['#2f5d8a', '#4a7c59', '#a4462f', '#b8892b', '#6b4f8a', '#3d7d7d']

/**
 * @param {number|string|null|undefined} userId
 * @returns {{name: string, initial: string, color: string, isKnown: boolean}}
 */
export function authorOf(userId) {
  const id = Number(userId)

  // id 拿不到（老数据、字段改名）时退回一个中性身份，不要让页面崩掉或者显示 undefined
  if (!Number.isFinite(id) || id <= 0) {
    return { name: '匿名冒险者', initial: '匿', color: '#6b6050', isKnown: false }
  }

  return {
    name: AUTHOR_NAMES[id % AUTHOR_NAMES.length],
    initial: AUTHOR_NAMES[id % AUTHOR_NAMES.length].charAt(0),
    color: AVATAR_PALETTE[id % AVATAR_PALETTE.length],
    isKnown: true,
  }
}
