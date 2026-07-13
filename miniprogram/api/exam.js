const { request } = require('./request')

// ============= 字段映射 =============
// 后端 Exam 字段 → 前端 list 卡片字段
// status 语义：前端用 'todo'/'doing'/'done'，badge 颜色依赖这个
function mapExamList(e, myRecordMap) {
  const id = e.id
  const record = myRecordMap.get(id)   // exam_record 中的对应记录
  return {
    id,
    title: e.title,
    duration: e.duration || 0,
    questionCount: e.questionCount || 0,
    totalScore: e.totalScore || 0,
    passScore: e.passScore || 0,
    maxRetry: e.maxRetry ?? 1,
    examType: e.examType ?? e.exam_type,
    courseId: e.courseId ?? e.course_id,
    // 已考 → done，否则 todo；doing 由本地答题页维护
    status: record ? 1 : 0,
    score: record ? record.score : null,
    recordId: record ? record.id : null,
    retryLeft: record
      ? Math.max(0, (e.maxRetry ?? 1) - (record.times || 1))
      : (e.maxRetry ?? 1)
  }
}

// exam_record 领域字段 → 前端显示
function mapRecord(r) {
  return {
    id: r.id,
    examId: r.examId ?? r.exam_id,
    examTitle: r.examTitle || r.title || '',
    score: r.score,
    passed: r.passed,
    correctCount: r.correctCount ?? r.correct_count,
    totalCount: r.totalCount ?? r.total_count ?? r.questionCount,
    duration: r.duration,
    submitTime: r.submitTime ?? r.submit_time ?? r.createTime,
    times: r.times || 1
  }
}

// PaperQuestionVO → 前端题目显示；options 可能是 JSON 字符串
function mapQuestion(q) {
  let options = q.options
  if (typeof options === 'string') {
    try { options = JSON.parse(options) } catch (e) { options = [] }
  }
  return {
    id: q.id,
    title: q.title,
    questionType: q.questionType ?? q.question_type,
    options: options || [],
    score: q.score || 0
  }
}

module.exports = {
  // 可参加的考试列表
  // 后端返回 PageResult<ExamListVO> = { records, total, pageNum, pageSize }
  // ExamListVO 已含学员维度字段：status/score/passed/times/retryLeft/recordId
  getList: () => request('/exam/list', 'GET').then(res => ((res && res.records) || [])),

  // 我的考试记录（用于在 list 列表上打标）
  getMyRecords: () => request('/exam/my-records', 'GET').then(list => (list || []).map(mapRecord)),

  // 开始考试
  startExam: (id) => request(`/exam/start/${id}`, 'POST')
    .then(data => ({
      id: data.examId,
      title: data.title,
      duration: data.duration,
      totalScore: data.totalScore,
      passScore: data.passScore,
      questions: (data.questions || []).map(mapQuestion)
    })),

  // 提交考试
  submitExam: (data) => request('/exam/submit', 'POST', data),

  // 考试记录详情
  getRecord: (id) => request(`/exam/record/${id}`, 'GET').then(mapRecord),

  // 考试结果详情（含 correctCount/wrongCount/unansweredCount/totalScore/correctRate）
  // 后端 /exam/result?examId=xxx 返回 ExamResultVO
  getResult: (examId) => request(`/exam/result?examId=${examId}`, 'GET')
}
