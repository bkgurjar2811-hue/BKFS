import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'server'))
from request_intake import accept_request


class IntakeTests(unittest.TestCase):
    def setUp(self):
        self.state = {'customers': [{'id': 'c1', 'name': 'Test Customer',
                                    'mobile': '+91 98765 43210', 'aadhaar': '123412341234'}],
                      'loanRequests': [], 'loans': [], 'tx': []}
        self.body = dict(requestId='test-request-00001', name='Test Customer',
                         mobile='9876543210', aadhaarLast4='1234', amount=25000,
                         tenure=12, frequency='Monthly', purpose='Test', note='')

    def test_registered_mobile_normalization(self):
        for number in ['9876543210', '+91 98765 43210', '09876543210']:
            with self.subTest(number=number):
                state=copy.deepcopy(self.state)
                response=accept_request(state, {**self.body, 'mobile': number})
                self.assertEqual(state['loanRequests'][0]['customerId'], 'c1')
                self.assertEqual(set(response), {'ok', 'receipt'})

    def test_unmatched_goes_to_review_without_creating_customer_or_loan(self):
        for body in [{**self.body, 'mobile': '9123456789'}, {**self.body, 'aadhaarLast4': '0000'}]:
            state=copy.deepcopy(self.state)
            accept_request(state, body)
            self.assertIsNone(state['loanRequests'][0]['customerId'])
            self.assertEqual(state['loanRequests'][0]['verification'], 'Needs review')
            self.assertEqual(len(state['customers']), 1)
            self.assertEqual(state['loans'], [])
            self.assertEqual(state['tx'], [])

    def test_ambiguous_records_require_review(self):
        self.state['customers'].append({**self.state['customers'][0], 'id': 'c2'})
        accept_request(self.state, self.body)
        self.assertIsNone(self.state['loanRequests'][0]['customerId'])

    def test_retry_has_same_receipt_and_one_record(self):
        first=accept_request(self.state, self.body)
        self.assertEqual(first, accept_request(self.state, self.body))
        self.assertEqual(len(self.state['loanRequests']), 1)
        with self.assertRaises(ValueError):
            accept_request(self.state, {**self.body, 'amount': 30000})

    def test_bad_input_never_saved(self):
        for key, value in [('mobile','43210'), ('aadhaarLast4',''), ('name',''),
                           ('amount',float('nan')), ('amount',float('inf')), ('amount',-1),
                           ('amount',1.001), ('tenure',1.5), ('tenure',0),
                           ('frequency','Weekly'), ('note','x'*1001)]:
            with self.subTest(key=key, value=value):
                with self.assertRaises(ValueError):
                    accept_request(self.state, {**self.body,key:value})
                self.assertEqual(self.state['loanRequests'], [])


if __name__ == '__main__':
    unittest.main()
