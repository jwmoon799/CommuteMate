"""Regression checks for article completeness and exclusion of non-editorial text."""
import unittest
from news_service import complete_story

class FullArticleTest(unittest.TestCase):
    def story(self, page):
        return complete_story({'title':'경제 뉴스','url':'https://www.newsis.com/view/test'},lambda _:page)

    def test_long_article_keeps_final_paragraph(self):
        paragraphs=''.join('<p>본문 문장 전체를 읽습니다.</p>' for _ in range(100))
        result=self.story('<article>'+paragraphs+'<p>마지막 문단입니다.</p></article>')
        self.assertTrue(result['text'].endswith('마지막 문단입니다.'))
        self.assertEqual(result['text'].count('본문 문장 전체를 읽습니다.'),100)
        self.assertEqual(result['contentMode'],'full')

    def test_skip_photos_ads_and_page_outside_article(self):
        body='경제 본문입니다. '*40
        result=self.story('<article><div class="thumCont"><div><p>사진 설명</p></div></div>'
                          '<div class="summury">요약 제외</div><script>광고 코드</script>'+body+
                          '</article><p>관련 기사 광고</p>')
        for unwanted in ['사진 설명','요약 제외','광고 코드','관련 기사 광고']:
            self.assertNotIn(unwanted,result['text'])

    def test_blocked_or_empty_page_is_not_an_excerpt_fallback(self):
        for page in ['<html>접근 제한</html>','<article>짧은 요약만</article>']:
            with self.assertRaises(RuntimeError):self.story(page)

if __name__=='__main__':unittest.main()
