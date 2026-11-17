"""Regression for the disappearing 'Toon volledige codeflow' panel."""
from html.parser import HTMLParser
from pathlib import Path
import unittest

STATIC=Path(__file__).parent/'static'

class Outline(HTMLParser):
 def __init__(self):super().__init__();self.stack=[];self.parents={}
 def handle_starttag(self,tag,attrs):
  args=dict(attrs)
  self.stack.append(tag)
  if 'id' in args:self.parents[args['id']]=list(self.stack)
 def handle_endtag(self,tag):
  if tag in self.stack:
   while self.stack:
    prev=self.stack.pop()
    if prev==tag:break

class FlowPanelTest(unittest.TestCase):
 def test_flow_is_persistent_section_outside_collapsible_details(self):
  parser=Outline();parser.feed((STATIC/'index.html').read_text())
  self.assertEqual(parser.parents['inspector-flow-section'][-1],'section')
  for key in ('inspector-flow','inspector-flow-btn','inspector-task'):
   self.assertNotIn('details',parser.parents[key])
  self.assertEqual(parser.parents['inspector-flow'][-1],'div')
 def test_only_latest_async_flow_response_may_replace_current_selection(self):
  javascript=(STATIC/'inspector.js').read_text()
  self.assertIn('requestId!==latestFlowRequest',javascript)
  self.assertIn("task!==el('inspector-task').value",javascript)
  self.assertIn("el('inspector-flow-section').hidden=false",javascript)

if __name__=='__main__':unittest.main()
