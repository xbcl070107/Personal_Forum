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

/** 全站共用的一份头像色板：顶栏、帖子作者、评论作者都用它 */
export const AVATAR_PALETTE = ['#2f5d8a', '#4a7c59', '#a4462f', '#b8892b', '#6b4f8a', '#3d7d7d']

/**
 * 按名字算一个稳定的颜色。
 *
 * <p>同一个人在哪都同一个色，靠的是「同一个名字算出同一个下标」。
 * 这里只是把字符码加起来取模，不是正经哈希 —— 目的只是颜色稳定，
 * 分布不均匀完全可以接受（多几个人撞色不算 bug）。
 *
 * <p>为什么按名字而不是按 userId：顶栏显示的是登录名、评论区给的也是
 * 用户名，只有这里统一按名字算，同一个人在这三处的颜色才真的对得上。
 */
export function avatarColorOf(name) {
  const s = String(name || '')
  if (!s) return '#6b6050'
  let sum = 0
  for (let i = 0; i < s.length; i += 1) sum += s.charCodeAt(i)
  return AVATAR_PALETTE[sum % AVATAR_PALETTE.length]
}

/**
 * 作者身份的「长相」——名字、首字、颜色。
 *
 * 【三个参数的分工】
 * 后端现在会给两个名字，它们不是一回事：
 *   nickname —— 展示名，**可以重复**。页面上要显示的就是它。
 *   username —— 登录名，**唯一**。不显示，但用来把两个同名的人区分开。
 *
 * 【为什么头像色的键用 username 而不是 nickname】
 * 昵称允许重复。拿昵称当哈希键的话，两个都叫「提灯人」的人会分到同一个颜色、
 * 同一个首字，在列表里完全分不出来。username 唯一，用它做键才能保证
 * 「同一个人永远同色」且「不同的人不会撞成一样」。
 *
 * 【为什么还留着按 userId 推名字的老逻辑】
 * 兜底。老缓存里没有 nickname、后端字段改名、或者别的接口只给 userId 时，
 * 至少页面不会显示 undefined，也不会崩。
 *
 * @param {number|string|null|undefined} userId
 * @param {string} [nickname] 昵称，展示用，可重复
 * @param {string} [username] 登录名，唯一，只用来算颜色
 * @returns {{name: string, initial: string, color: string, isKnown: boolean}}
 */
export function authorOf(userId, nickname, username) {
  const id = Number(userId)
  const nick = typeof nickname === 'string' ? nickname.trim() : ''
  const uname = typeof username === 'string' ? username.trim() : ''

  // ① 有名字 —— 正常路径。昵称优先，昵称为空才退回用户名
  if (nick || uname) {
    const name = nick || uname
    return {
      name,
      initial: name.charAt(0).toUpperCase(),
      // 颜色键优先用唯一的 username
      color: avatarColorOf(uname || nick),
      isKnown: true,
    }
  }

  // ② 什么名字都没有，也没有有效 id —— 给个中性身份，别显示 undefined
  if (!Number.isFinite(id) || id <= 0) {
    return { name: '匿名冒险者', initial: '匿', color: '#6b6050', isKnown: false }
  }

  // ③ 只有 id：退回推导。同一个人仍然稳定，只是名字未必是真名
  const name = DERIVED_NAMES[id % DERIVED_NAMES.length]
  return {
    name,
    initial: name.charAt(0),
    color: avatarColorOf(name),
    isKnown: true,
  }
}

/**
 * 只有 userId、没有用户名时的兜底名字表。
 * 不是真实用户名，纯粹为了让页面别显示成一串数字。
 */
const DERIVED_NAMES = [
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
