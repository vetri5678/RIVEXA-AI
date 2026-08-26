import React, { useState } from 'react';
import { X, Check, Loader2, Key, Settings } from 'lucide-react';
import { useCreateRepository, useValidateToken } from '../../hooks/useRepository';
import type { RepositoryCreateRequest, GitProvider, Visibility, PredictionFrequency } from '../../types/repository';

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

type Step = 1 | 2 | 3 | 4;

export const AddRepositoryWizard: React.FC<Props> = ({ isOpen, onClose }) => {
  const [step, setStep] = useState<Step>(1);
  const [form, setForm] = useState<Partial<RepositoryCreateRequest>>({
    repositoryName: '',
    description: '',
    organization: '',
    owner: '',
    repositoryUrl: '',
    gitProvider: 'GITHUB',
    branch: 'main',
    technology: '',
    language: '',
    projectType: '',
    visibility: 'PRIVATE',
    license: '',
    predictionFrequency: 'WEEKLY',
    autoPredictionEnabled: true,
    notificationsEnabled: true,
    backgroundSyncEnabled: true,
    reportGenerationEnabled: false,
    authTokenHint: '',
    webhookSecret: '',
  });

  const [validationToken, setValidationToken] = useState('');
  const [isValidating, setIsValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<{ success: boolean; message: string } | null>(null);

  const createMutation = useCreateRepository();
  const validateMutation = useValidateToken();

  if (!isOpen) return null;

  const handleNext = () => {
    if (step < 4) setStep((step + 1) as Step);
  };

  const handleBack = () => {
    if (step > 1) setStep((step - 1) as Step);
  };

  const handleChange = (fields: Partial<RepositoryCreateRequest>) => {
    setForm(prev => ({ ...prev, ...fields }));
  };

  const handleValidate = async () => {
    if (!form.repositoryUrl || !form.gitProvider) return;
    setIsValidating(true);
    setValidationResult(null);
    try {
      const res = await validateMutation.mutateAsync({
        gitProvider: form.gitProvider,
        token: validationToken,
        repositoryUrl: form.repositoryUrl,
      });
      setValidationResult({ success: res.valid, message: res.message });
    } catch (e: any) {
      setValidationResult({
        success: false,
        message: e.response?.data?.error || 'Validation failed. Verify connection settings.',
      });
    } finally {
      setIsValidating(false);
    }
  };

  const handleSubmit = async () => {
    try {
      await createMutation.mutateAsync({
        ...form,
        authTokenHint: validationToken ? `Ends with ****${validationToken.slice(-4)}` : undefined,
      } as RepositoryCreateRequest);
      setStep(4);
    } catch (e: any) {
      alert(e.response?.data?.error || 'Failed to create repository');
    }
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-3 sm:p-4 overflow-y-auto">
      <div className="glass-panel w-full max-w-2xl max-h-[90vh] flex flex-col my-auto border border-glass-border shadow-2xl rounded-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-4 sm:px-6 py-4 border-b border-glass-border bg-cyber-900/40">
          <div>
            <h3 className="text-xs sm:text-sm font-mono font-bold text-slate-100 uppercase tracking-wider">
              Register Repository Wizard
            </h3>
            <p className="text-[10px] text-slate-500 font-mono mt-0.5">
              Add new software repository node to AI Prediction Pipeline
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-cyber-800 transition-all cursor-pointer"
          >
            <X size={16} />
          </button>
        </div>

        {/* Steps navigation indicator */}
        <div className="grid grid-cols-4 border-b border-glass-border bg-cyber-900/20 text-center py-2.5 text-[10px] font-mono px-2">
          {[
            { label: 'Basic Info', num: 1 },
            { label: 'Connection', num: 2 },
            { label: 'AI Config', num: 3 },
            { label: 'Verification', num: 4 },
          ].map(s => (
            <div
              key={s.num}
              className={`flex flex-col items-center gap-1 ${
                step === s.num
                  ? 'text-neon-blue font-bold'
                  : step > s.num
                  ? 'text-neon-green'
                  : 'text-slate-500'
              }`}
            >
              <div
                className={`w-5 h-5 rounded-full flex items-center justify-center border text-[9px] ${
                  step === s.num
                    ? 'border-neon-blue bg-neon-blue/10'
                    : step > s.num
                    ? 'border-neon-green bg-neon-green/10'
                    : 'border-slate-800'
                }`}
              >
                {step > s.num ? '✓' : s.num}
              </div>
              <span className="uppercase tracking-widest hidden sm:block">{s.label}</span>
            </div>
          ))}
        </div>

        {/* Form Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4 font-mono text-xs">
          {/* STEP 1: Basic Info */}
          {step === 1 && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Repo Name *</label>
                  <input
                    type="text"
                    required
                    className="glass-input w-full"
                    placeholder="my-cool-project"
                    value={form.repositoryName || ''}
                    onChange={e => handleChange({ repositoryName: e.target.value })}
                  />
                </div>
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Repository URL *</label>
                  <input
                    type="text"
                    required
                    className="glass-input w-full"
                    placeholder="https://github.com/org/repo"
                    value={form.repositoryUrl || ''}
                    onChange={e => handleChange({ repositoryUrl: e.target.value })}
                  />
                </div>
              </div>

              <div>
                <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Description</label>
                <textarea
                  className="glass-input w-full h-20 resize-none py-2"
                  placeholder="Analyze core AI predictive pipeline repositories..."
                  value={form.description || ''}
                  onChange={e => handleChange({ description: e.target.value })}
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Organization</label>
                  <input
                    type="text"
                    className="glass-input w-full"
                    placeholder="rivexa-ai"
                    value={form.organization || ''}
                    onChange={e => handleChange({ organization: e.target.value })}
                  />
                </div>
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Owner / Maintainer</label>
                  <input
                    type="text"
                    className="glass-input w-full"
                    placeholder="graveyard-team"
                    value={form.owner || ''}
                    onChange={e => handleChange({ owner: e.target.value })}
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Git Provider *</label>
                  <select
                    className="glass-input w-full py-1.5"
                    value={form.gitProvider || 'GITHUB'}
                    onChange={e => handleChange({ gitProvider: e.target.value as GitProvider })}
                  >
                    <option value="GITHUB">GitHub</option>
                    <option value="GITLAB">GitLab</option>
                    <option value="BITBUCKET">Bitbucket</option>
                    <option value="AZURE_DEVOPS">Azure DevOps</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Default Branch</label>
                  <input
                    type="text"
                    className="glass-input w-full"
                    placeholder="main"
                    value={form.branch || 'main'}
                    onChange={e => handleChange({ branch: e.target.value })}
                  />
                </div>
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Visibility</label>
                  <select
                    className="glass-input w-full py-1.5"
                    value={form.visibility || 'PRIVATE'}
                    onChange={e => handleChange({ visibility: e.target.value as Visibility })}
                  >
                    <option value="PRIVATE">Private</option>
                    <option value="PUBLIC">Public</option>
                    <option value="INTERNAL">Internal</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Primary Language</label>
                  <input
                    type="text"
                    className="glass-input w-full"
                    placeholder="Java, TypeScript"
                    value={form.language || ''}
                    onChange={e => handleChange({ language: e.target.value })}
                  />
                </div>
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Tech Stack</label>
                  <input
                    type="text"
                    className="glass-input w-full"
                    placeholder="Spring Boot, React"
                    value={form.technology || ''}
                    onChange={e => handleChange({ technology: e.target.value })}
                  />
                </div>
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5 font-sans">License</label>
                  <input
                    type="text"
                    className="glass-input w-full"
                    placeholder="MIT, Apache-2.0"
                    value={form.license || ''}
                    onChange={e => handleChange({ license: e.target.value })}
                  />
                </div>
              </div>
            </div>
          )}

          {/* STEP 2: Connection */}
          {step === 2 && (
            <div className="space-y-4">
              <div className="bg-cyber-900/40 border border-glass-border p-4 rounded-xl flex gap-3">
                <Key className="text-neon-blue shrink-0 mt-0.5" size={18} />
                <div>
                  <h4 className="font-bold text-slate-200 uppercase tracking-wider">Git Provider Authentication</h4>
                  <p className="text-[10px] text-slate-500 mt-0.5 leading-relaxed">
                    Provide a Personal Access Token (PAT) with read repository scopes to authorize health and telemetry syncing.
                  </p>
                </div>
              </div>

              <div>
                <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Authentication Token</label>
                <input
                  type="password"
                  className="glass-input w-full font-sans"
                  placeholder="ghp_xxxxxxxxxxxx / glpat-xxxxxxxxxxxx"
                  value={validationToken}
                  onChange={e => setValidationToken(e.target.value)}
                />
              </div>

              <div>
                <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Webhook Secret (Optional)</label>
                <input
                  type="password"
                  className="glass-input w-full font-sans"
                  placeholder="Secret key for future webhook verification"
                  value={form.webhookSecret || ''}
                  onChange={e => handleChange({ webhookSecret: e.target.value })}
                />
              </div>

              {/* Action Buttons inside step */}
              <div className="flex gap-3">
                <button
                  type="button"
                  onClick={handleValidate}
                  disabled={isValidating || !validationToken}
                  className="btn-cyber-primary py-2 px-4 text-xs disabled:opacity-50"
                >
                  {isValidating ? (
                    <>
                      <Loader2 size={13} className="animate-spin" /> Verifying Connection...
                    </>
                  ) : (
                    'Verify Connection'
                  )}
                </button>
              </div>

              {validationResult && (
                <div
                  className={`p-3 rounded-lg border text-xs font-mono flex items-start gap-2 ${
                    validationResult.success
                      ? 'border-neon-green/20 bg-neon-green/5 text-neon-green'
                      : 'border-neon-pink/20 bg-neon-pink/5 text-neon-pink'
                  }`}
                >
                  <span className="text-md leading-none mt-0.5">{validationResult.success ? '✓' : '✗'}</span>
                  <div>
                    <span className="font-bold">{validationResult.success ? 'Nominal:' : 'Failed:'}</span>{' '}
                    {validationResult.message}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* STEP 3: Configuration */}
          {step === 3 && (
            <div className="space-y-4">
              <div className="bg-cyber-900/40 border border-glass-border p-4 rounded-xl flex gap-3">
                <Settings className="text-neon-blue shrink-0 mt-0.5" size={18} />
                <div>
                  <h4 className="font-bold text-slate-200 uppercase tracking-wider">Telemetry & Sync configuration</h4>
                  <p className="text-[10px] text-slate-500 mt-0.5 leading-relaxed">
                    Set up predictions frequency and auto-sync options for this repository.
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-slate-400 uppercase tracking-wider mb-1.5">Prediction Frequency</label>
                  <select
                    className="glass-input w-full py-1.5"
                    value={form.predictionFrequency || 'WEEKLY'}
                    onChange={e => handleChange({ predictionFrequency: e.target.value as PredictionFrequency })}
                  >
                    <option value="DAILY">Daily</option>
                    <option value="WEEKLY">Weekly</option>
                    <option value="MONTHLY">Monthly</option>
                    <option value="MANUAL">Manual (On-Demand Only)</option>
                  </select>
                </div>
              </div>

              <div className="space-y-3 pt-2">
                {[
                  {
                    id: 'autoPredictionEnabled',
                    title: 'Enable Automatic AI Prediction',
                    desc: 'Trigger new ML failure assessments on every synchronization run.',
                  },
                  {
                    id: 'notificationsEnabled',
                    title: 'Enable Alerts and Notifications',
                    desc: 'Alert analysts via Slack / Email if failure probability triggers high risk.',
                  },
                  {
                    id: 'backgroundSyncEnabled',
                    title: 'Enable Background Sync',
                    desc: 'Automated nightly sync pipeline for Git metadata, commit count and issues.',
                  },
                  {
                    id: 'reportGenerationEnabled',
                    title: 'Auto-Generate Monthly PDF Reports',
                    desc: 'Assemble executive summaries and charts into export-ready docs.',
                  },
                ].map(item => (
                  <label key={item.id} className="flex items-start gap-3 cursor-pointer">
                    <input
                      type="checkbox"
                      className="mt-1 accent-neon-blue w-4 h-4"
                      checked={!!form[item.id as keyof RepositoryCreateRequest]}
                      onChange={e => handleChange({ [item.id]: e.target.checked })}
                    />
                    <div>
                      <div className="text-xs font-bold text-slate-200">{item.title}</div>
                      <div className="text-[10px] text-slate-500 mt-0.5 leading-normal">{item.desc}</div>
                    </div>
                  </label>
                ))}
              </div>
            </div>
          )}

          {/* STEP 4: Validation Success Screen */}
          {step === 4 && (
            <div className="flex flex-col items-center justify-center py-8 text-center space-y-4">
              <div className="w-16 h-16 rounded-full bg-neon-green/10 border border-neon-green/30 flex items-center justify-center text-neon-green shadow-[0_0_20px_rgba(0,255,136,0.1)]">
                <Check size={32} />
              </div>
              <div>
                <h4 className="text-sm font-bold text-slate-100 uppercase tracking-widest">
                  Repository Registered Successfully
                </h4>
                <p className="text-[10px] text-slate-500 mt-1 max-w-sm mx-auto leading-relaxed">
                  Node '{form.repositoryName}' is now active and monitoring. Heuristic prediction matrix has been bootstrapped.
                </p>
              </div>
              <div className="w-full max-w-sm bg-cyber-900/40 border border-glass-border rounded-xl p-4 text-left space-y-2">
                <div className="flex justify-between border-b border-cyber-800/30 pb-1.5">
                  <span className="text-slate-500">Repository Name</span>
                  <span className="text-slate-200 font-bold">{form.repositoryName}</span>
                </div>
                <div className="flex justify-between border-b border-cyber-800/30 pb-1.5">
                  <span className="text-slate-500">URL</span>
                  <span className="text-slate-400 font-mono text-[10px] truncate max-w-[200px]" title={form.repositoryUrl}>
                    {form.repositoryUrl}
                  </span>
                </div>
                <div className="flex justify-between border-b border-cyber-800/30 pb-1.5">
                  <span className="text-slate-500">Provider</span>
                  <span className="text-slate-300">{form.gitProvider}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Sync Config</span>
                  <span className="text-neon-purple">{form.predictionFrequency}</span>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Footer actions */}
        <div className="px-6 py-4 border-t border-glass-border bg-cyber-900/40 flex justify-between items-center">
          {step === 4 ? (
            <button
              onClick={onClose}
              className="btn-cyber-primary text-xs py-2 px-6 ml-auto"
            >
              Go to Repositories
            </button>
          ) : (
            <>
              <button
                onClick={handleBack}
                disabled={step === 1}
                className="btn-cyber-secondary text-xs py-2 px-4 disabled:opacity-30 disabled:cursor-not-allowed"
              >
                Back
              </button>

              <div className="flex gap-2">
                <button
                  onClick={onClose}
                  className="btn-cyber-secondary text-xs py-2 px-4"
                >
                  Cancel
                </button>
                {step === 3 ? (
                  <button
                    onClick={handleSubmit}
                    disabled={createMutation.isPending || !form.repositoryName || !form.repositoryUrl}
                    className="btn-cyber-primary text-xs py-2 px-6 disabled:opacity-50"
                  >
                    {createMutation.isPending ? (
                      <>
                        <Loader2 size={13} className="animate-spin" /> Submitting...
                      </>
                    ) : (
                      'Register Node'
                    )}
                  </button>
                ) : (
                  <button
                    onClick={handleNext}
                    disabled={step === 1 && (!form.repositoryName || !form.repositoryUrl)}
                    className="btn-cyber-primary text-xs py-2 px-6 disabled:opacity-50"
                  >
                    Next
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default AddRepositoryWizard;
