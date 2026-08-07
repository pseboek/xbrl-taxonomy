import sys
from pathlib import Path
sys.path.insert(0, str(Path('arelle/lib').resolve()))
from arelle import Cntlr

entry = Path('xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd').resolve()

cntlr = Cntlr.Cntlr(logFileName='logToPrint')
model_xbrl = cntlr.modelManager.load(str(entry))
print('loaded', bool(model_xbrl), 'concept_count', len(model_xbrl.qnameConcepts) if model_xbrl else -1)
if model_xbrl:
    print('has_errors', model_xbrl.errors[:5] if hasattr(model_xbrl, 'errors') else 'n/a')
    model_xbrl.close()
cntlr.close()
